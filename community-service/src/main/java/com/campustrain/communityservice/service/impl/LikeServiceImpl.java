package com.campustrain.communityservice.service.impl;

import com.campustrain.communityservice.config.LikeKeys;
import com.campustrain.communityservice.context.UserContext;
import com.campustrain.communityservice.entity.Answer;
import com.campustrain.communityservice.entity.Question;
import com.campustrain.communityservice.exception.CommunityException;
import com.campustrain.communityservice.mapper.CommunityMapper;
import com.campustrain.communityservice.service.LikeService;
import com.campustrain.communityservice.vo.LikeView;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
public class LikeServiceImpl implements LikeService {
    private final CommunityMapper communityMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedissonClient redissonClient;
    private final TransactionTemplate transactionTemplate;
    private final int batchSize;
    private final DefaultRedisScript<Long> initializeLikeScript = loadScript("lua/like-initialize.lua");
    private final DefaultRedisScript<Long> addLikeScript = loadScript("lua/like-add.lua");
    private final DefaultRedisScript<Long> removeLikeScript = loadScript("lua/like-remove.lua");
    private final DefaultRedisScript<Long> acknowledgeDirtyScript = loadScript("lua/like-ack-dirty.lua");

    public LikeServiceImpl(CommunityMapper communityMapper, StringRedisTemplate stringRedisTemplate, RedissonClient redissonClient,
                           TransactionTemplate transactionTemplate,
                           @Value("${campustrain.community.like.flush-batch-size:100}") int batchSize) {
        this.communityMapper = communityMapper; this.stringRedisTemplate = stringRedisTemplate;
        this.redissonClient = redissonClient; this.transactionTemplate = transactionTemplate;
        this.batchSize = batchSize;
    }

    @Override
    public LikeView likeTarget(String type, long targetId) { return changeLikeState(type, targetId, true); }

    @Override
    public LikeView unlikeTarget(String type, long targetId) { return changeLikeState(type, targetId, false); }

    private LikeView changeLikeState(String rawType, long targetId, boolean like) {
        String type = LikeService.targetType(rawType);
        long userId = requireUserId();
        persistedCount(type, targetId);
        try {
            ensureLikeStateInitialized(type, targetId);
            Long changed = stringRedisTemplate.execute(like ? addLikeScript : removeLikeScript, likeKeys(type, targetId),
                    String.valueOf(userId), LikeKeys.member(type, targetId));
            if (changed == null || changed < 0) throw new IllegalStateException("like state not initialized");
            if (changed == 0) log.warn("repeat like operation, traceId={}, userId={}, targetType={}, targetId={}, like={}",
                    UserContext.traceId(), userId, type, targetId, like);
            LikeView state = getRedisLikeState(type, targetId, userId);
            log.debug("like operation, traceId={}, userId={}, targetType={}, targetId={}, like={}, changed={}",
                    UserContext.traceId(), userId, type, targetId, like, changed);
            return new LikeView(type, targetId, state.likeCount(), state.likedByMe(), changed == 1);
        } catch (CommunityException ex) { throw ex; }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw likeRedisUnavailable(type, targetId, userId, ex);
        }
        catch (Exception ex) { throw likeRedisUnavailable(type, targetId, userId, ex); }
    }

    @Override
    public LikeView getLikeState(String rawType, long targetId, long persistedCount) {
        String type = LikeService.targetType(rawType);
        Long userId = UserContext.current() == null ? null : UserContext.current().userId();
        try {
            ensureLikeStateInitialized(type, targetId);
            return getRedisLikeState(type, targetId, userId);
        } catch (Exception ex) {
            log.warn("like read Redis unavailable; using MySQL, traceId={}, targetType={}, targetId={}",
                    UserContext.traceId(), type, targetId, ex);
            return new LikeView(type, targetId, persistedCount,
                    userId != null && communityMapper.selectActiveLikers(type, targetId).contains(String.valueOf(userId)), false);
        }
    }

    @Override
    public LikeView getLikeState(String type, long targetId) {
        type = LikeService.targetType(type);
        return getLikeState(type, targetId, persistedCount(type, targetId));
    }

    @Override
    public Map<Long, LikeView> getLikeStates(String rawType, Map<Long, Long> persistedCounts) {
        String type = LikeService.targetType(rawType);
        if (persistedCounts.isEmpty()) return Map.of();
        Long userId = UserContext.current() == null ? null : UserContext.current().userId();
        List<Long> ids = new ArrayList<>(persistedCounts.keySet());
        try {
            List<Object> values = readBatchValuesFromRedis(type, ids, userId);
            for (int i = 0; i < ids.size(); i++) {
                if (values.get(i * 2) == null) ensureLikeStateInitialized(type, ids.get(i));
            }
            if (values.stream().anyMatch(java.util.Objects::isNull)) values = readBatchValuesFromRedis(type, ids, userId);
            Map<Long, LikeView> result = new LinkedHashMap<>();
            for (int i = 0; i < ids.size(); i++) {
                long id = ids.get(i);
                Object count = values.get(i * 2);
                result.put(id, new LikeView(type, id, count == null ? persistedCounts.get(id) : Long.parseLong(count.toString()),
                        Boolean.TRUE.equals(values.get(i * 2 + 1)), false));
            }
            return result;
        } catch (Exception ex) {
            log.warn("batch like read Redis unavailable; using MySQL, traceId={}, targetType={}",
                    UserContext.traceId(), type, ex);
            Set<Long> liked = userId == null ? Set.of() : communityMapper.selectLikedTargetIds(type, new HashSet<>(ids), userId);
            Map<Long, LikeView> result = new LinkedHashMap<>();
            persistedCounts.forEach((id, count) -> result.put(id, new LikeView(type, id, count, liked.contains(id), false)));
            return result;
        }
    }

    private List<Object> readBatchValuesFromRedis(String type, List<Long> ids, Long userId) {
        return stringRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            var serializer = stringRedisTemplate.getStringSerializer();
            for (Long id : ids) {
                connection.stringCommands().get(serializer.serialize(LikeKeys.count(type, id)));
                if (userId == null) connection.setCommands().sIsMember(
                        serializer.serialize(LikeKeys.users(type, id)), serializer.serialize(""));
                else connection.setCommands().sIsMember(
                        serializer.serialize(LikeKeys.users(type, id)), serializer.serialize(String.valueOf(userId)));
            }
            return null;
        });
    }

    private LikeView getRedisLikeState(String type, long targetId, Long userId) {
        String count = stringRedisTemplate.opsForValue().get(LikeKeys.count(type, targetId));
        Boolean liked = userId == null ? false : stringRedisTemplate.opsForSet().isMember(LikeKeys.users(type, targetId), String.valueOf(userId));
        return new LikeView(type, targetId, count == null ? 0 : Long.parseLong(count), Boolean.TRUE.equals(liked), false);
    }

    private long persistedCount(String type, long targetId) {
        Question question = "QUESTION".equals(type) ? communityMapper.selectQuestionById(targetId) : null;
        Answer answer = "ANSWER".equals(type) ? communityMapper.selectAnswerById(targetId) : null;
        if (question == null && answer == null) throw new CommunityException(404, "点赞目标不存在");
        return question != null ? question.likeCount() : answer.likeCount();
    }

    private long requireUserId() {
        if (UserContext.current() == null || UserContext.current().userId() == null)
            throw new CommunityException(401, "用户身份缺失");
        return UserContext.current().userId();
    }

    private List<String> likeKeys(String type, long targetId) {
        return List.of(LikeKeys.users(type, targetId), LikeKeys.count(type, targetId),
                LikeKeys.revision(type, targetId), LikeKeys.initialized(type, targetId), LikeKeys.dirty());
    }

    private void ensureLikeStateInitialized(String type, long targetId) throws InterruptedException {
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(LikeKeys.initialized(type, targetId)))) return;
        RLock lock = redissonClient.getLock(LikeKeys.lock(type, targetId));
        boolean acquired = lock.tryLock(3, TimeUnit.SECONDS);
        if (!acquired) throw new IllegalStateException("like initialization lock timed out");
        try {
            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(LikeKeys.initialized(type, targetId)))) return;
            Set<String> persistedUsers = communityMapper.selectActiveLikers(type, targetId);
            stringRedisTemplate.execute(initializeLikeScript, likeKeys(type, targetId).subList(0, 4), persistedUsers.toArray(String[]::new));
            log.info("like state initialized, traceId={}, targetType={}, targetId={}, users={}",
                    UserContext.traceId(), type, targetId, persistedUsers.size());
        } finally {
            if (lock.isHeldByCurrentThread()) lock.unlock();
        }
    }

    @Scheduled(fixedDelayString = "${campustrain.community.like.flush-interval-ms:5000}")
    public void flushDirtyLikes() {
        List<String> members = new ArrayList<>();
        try (Cursor<String> cursor = stringRedisTemplate.opsForSet().scan(LikeKeys.dirty(),
                ScanOptions.scanOptions().count(batchSize).build())) {
            while (cursor.hasNext() && members.size() < batchSize) members.add(cursor.next());
        } catch (Exception ex) {
            log.error("like dirty scan failed", ex);
            return;
        }
        int saved = 0;
        for (String member : members) {
            try { if (flushLikeTarget(member)) saved++; }
            catch (Exception ex) { log.error("like flush failed, member={}", member, ex); }
        }
        if (!members.isEmpty()) log.info("like flush batch, requested={}, saved={}", members.size(), saved);
    }

    public boolean flushLikeTarget(String member) throws InterruptedException {
        String[] parts = member.split(":", 2);
        if (parts.length != 2) throw new IllegalArgumentException("invalid like dirty member");
        String type = LikeService.targetType(parts[0]);
        long targetId = Long.parseLong(parts[1]);
        RLock lock = redissonClient.getLock(LikeKeys.flushLock(type, targetId));
        if (!lock.tryLock(3, TimeUnit.SECONDS)) return false;
        try {
            String revision = stringRedisTemplate.opsForValue().get(LikeKeys.revision(type, targetId));
            if (revision == null) throw new IllegalStateException("missing like revision");
            Set<String> members = stringRedisTemplate.opsForSet().members(LikeKeys.users(type, targetId));
            Set<String> snapshot = members == null ? Set.of() : Set.copyOf(members);
            transactionTemplate.executeWithoutResult(status -> synchronizeLikes(type, targetId, snapshot));
            Long removed = stringRedisTemplate.execute(acknowledgeDirtyScript, List.of(LikeKeys.revision(type, targetId), LikeKeys.dirty()),
                    revision, member);
            if (removed == null || removed == 0) {
                log.warn("old like revision retained dirty, targetType={}, targetId={}, revision={}",
                        type, targetId, revision);
                return false;
            }
            return true;
        } finally {
            if (lock.isHeldByCurrentThread()) lock.unlock();
        }
    }

    private void synchronizeLikes(String type, long targetId, Set<String> desired) {
        Set<String> current = communityMapper.selectActiveLikers(type, targetId);
        for (String user : desired) {
            if (!current.contains(user)) communityMapper.upsertActiveLike(Long.parseLong(user), type, targetId);
        }
        for (String user : current) {
            if (!desired.contains(user)) communityMapper.deactivateLike(Long.parseLong(user), type, targetId);
        }
        int updated = "QUESTION".equals(type)
                ? communityMapper.updateQuestionLikeCount(targetId, desired.size())
                : communityMapper.updateAnswerLikeCount(targetId, desired.size());
        if (updated != 1) throw new IllegalStateException("like count update failed");
    }

    private CommunityException likeRedisUnavailable(String type, long id, long userId, Exception ex) {
        log.error("like Redis unavailable, traceId={}, userId={}, targetType={}, targetId={}",
                UserContext.traceId(), userId, type, id, ex);
        return new CommunityException(503, "点赞服务暂时不可用");
    }

    private static DefaultRedisScript<Long> loadScript(String location) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(location));
        script.setResultType(Long.class);
        return script;
    }
}
