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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Objects;
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
        this.communityMapper = communityMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.redissonClient = redissonClient;
        this.transactionTemplate = transactionTemplate;
        this.batchSize = batchSize;
    }

    @Override
    public LikeView likeTarget(String targetType, long targetId) {
        return changeLikeState(targetType, targetId, true);
    }

    @Override
    public LikeView unlikeTarget(String targetType, long targetId) {
        return changeLikeState(targetType, targetId, false);
    }

    private LikeView changeLikeState(String rawTargetType, long targetId, boolean like) {
        String targetType = LikeService.normalizeTargetType(rawTargetType);
        long userId = requireUserId();
        persistedCount(targetType, targetId);
        try {
            ensureLikeStateInitialized(targetType, targetId);
            Long changed = stringRedisTemplate.execute(like ? addLikeScript : removeLikeScript, likeKeys(targetType, targetId),
                    String.valueOf(userId), LikeKeys.member(targetType, targetId));
            if (changed == null || changed < 0) throw new IllegalStateException("like state not initialized");
            if (changed == 0) log.warn("repeat like operation, traceId={}, userId={}, targetType={}, targetId={}, like={}",
                    UserContext.traceId(), userId, targetType, targetId, like);
            LikeView state = getRedisLikeState(targetType, targetId, userId);
            log.debug("like operation, traceId={}, userId={}, targetType={}, targetId={}, like={}, changed={}",
                    UserContext.traceId(), userId, targetType, targetId, like, changed);
            return new LikeView(targetType, targetId, state.likeCount(), state.likedByMe(), changed == 1);
        } catch (CommunityException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw likeRedisUnavailable(targetType, targetId, userId, ex);
        } catch (Exception ex) {
            throw likeRedisUnavailable(targetType, targetId, userId, ex);
        }
    }

    @Override
    public LikeView getLikeState(String rawTargetType, long targetId, long persistedCount) {
        String targetType = LikeService.normalizeTargetType(rawTargetType);
        Long userId = UserContext.current() == null ? null : UserContext.current().userId();
        try {
            ensureLikeStateInitialized(targetType, targetId);
            return getRedisLikeState(targetType, targetId, userId);
        } catch (Exception ex) {
            log.warn("like read Redis unavailable; using MySQL, traceId={}, targetType={}, targetId={}",
                    UserContext.traceId(), targetType, targetId, ex);
            return new LikeView(targetType, targetId, persistedCount,
                    userId != null && communityMapper.selectActiveLikers(targetType, targetId).contains(String.valueOf(userId)), false);
        }
    }

    @Override
    public LikeView getLikeState(String targetType, long targetId) {
        String normalizedTargetType = LikeService.normalizeTargetType(targetType);
        return getLikeState(normalizedTargetType, targetId, persistedCount(normalizedTargetType, targetId));
    }

    //一次性批量查询一组问题/回答的“实时点赞数 + 当前用户是否点赞”，优先查 Redis，Redis 出问题就回退 MySQL。
    @Override
    public Map<Long, LikeView> getLikeStates(String rawTargetType, Map<Long, Long> persistedCounts) {
        String targetType = LikeService.normalizeTargetType(rawTargetType);
        if (persistedCounts.isEmpty()) return Map.of();
        Long userId = UserContext.current() == null ? null : UserContext.current().userId();
        List<Long> targetIds = new ArrayList<>(persistedCounts.keySet());
        try {
            List<Object> values = readBatchValuesFromRedis(targetType, targetIds, userId);
            for (int i = 0; i < targetIds.size(); i++) {
                if (values.get(i * 2) == null) ensureLikeStateInitialized(targetType, targetIds.get(i));
            }
            if (values.stream().anyMatch(Objects::isNull)) values = readBatchValuesFromRedis(targetType, targetIds, userId);
            Map<Long, LikeView> result = new LinkedHashMap<>();
            for (int i = 0; i < targetIds.size(); i++) {
                long targetId = targetIds.get(i);
                Object count = values.get(i * 2);
                result.put(targetId, new LikeView(targetType, targetId, count == null ? persistedCounts.get(targetId) : Long.parseLong(count.toString()),
                        Boolean.TRUE.equals(values.get(i * 2 + 1)), false));
            }
            return result;
        } catch (Exception ex) {
            log.warn("batch like read Redis unavailable; using MySQL, traceId={}, targetType={}",
                    UserContext.traceId(), targetType, ex);
            Set<Long> liked = userId == null ? Set.of() : communityMapper.selectLikedTargetIds(targetType, new HashSet<>(targetIds), userId);
            Map<Long, LikeView> result = new LinkedHashMap<>();
            persistedCounts.forEach((targetId, count) -> result.put(targetId, new LikeView(targetType, targetId, count, liked.contains(targetId), false)));
            return result;
        }
    }

    private List<Object> readBatchValuesFromRedis(String targetType, List<Long> targetIds, Long userId) {
        return stringRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            var serializer = stringRedisTemplate.getStringSerializer();
            for (Long targetId : targetIds) {
                connection.stringCommands().get(serializer.serialize(LikeKeys.count(targetType, targetId)));
                if (userId == null) connection.setCommands().sIsMember(
                        serializer.serialize(LikeKeys.users(targetType, targetId)), serializer.serialize(""));
                else connection.setCommands().sIsMember(
                        serializer.serialize(LikeKeys.users(targetType, targetId)), serializer.serialize(String.valueOf(userId)));
            }
            return null;
        });
    }

    private LikeView getRedisLikeState(String targetType, long targetId, Long userId) {
        String count = stringRedisTemplate.opsForValue().get(LikeKeys.count(targetType, targetId));
        Boolean liked = userId == null ? false : stringRedisTemplate.opsForSet().isMember(LikeKeys.users(targetType, targetId), String.valueOf(userId));
        return new LikeView(targetType, targetId, count == null ? 0 : Long.parseLong(count), Boolean.TRUE.equals(liked), false);
    }

    private long persistedCount(String targetType, long targetId) {
        Question question = "QUESTION".equals(targetType) ? communityMapper.selectQuestionById(targetId) : null;
        Answer answer = "ANSWER".equals(targetType) ? communityMapper.selectAnswerById(targetId) : null;
        if (question == null && answer == null) throw new CommunityException(404, "点赞目标不存在");
        return question != null ? question.likeCount() : answer.likeCount();
    }

    private long requireUserId() {
        if (UserContext.current() == null || UserContext.current().userId() == null)
            throw new CommunityException(401, "用户身份缺失");
        return UserContext.current().userId();
    }

    private List<String> likeKeys(String targetType, long targetId) {
        return List.of(LikeKeys.users(targetType, targetId), LikeKeys.count(targetType, targetId),
                LikeKeys.revision(targetType, targetId), LikeKeys.initialized(targetType, targetId), LikeKeys.dirty());
    }

    private void ensureLikeStateInitialized(String targetType, long targetId) throws InterruptedException {
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(LikeKeys.initialized(targetType, targetId)))) return;
        RLock lock = redissonClient.getLock(LikeKeys.lock(targetType, targetId));
        boolean acquired = lock.tryLock(3, TimeUnit.SECONDS);
        if (!acquired) throw new IllegalStateException("like initialization lock timed out");
        try {
            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(LikeKeys.initialized(targetType, targetId)))) return;
            Set<String> persistedUsers = communityMapper.selectActiveLikers(targetType, targetId);
            stringRedisTemplate.execute(initializeLikeScript, likeKeys(targetType, targetId).subList(0, 4), persistedUsers.toArray(String[]::new));
            log.info("like state initialized, traceId={}, targetType={}, targetId={}, users={}",
                    UserContext.traceId(), targetType, targetId, persistedUsers.size());
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
            try {
                if (flushLikeTarget(member)) saved++;
            } catch (Exception ex) {
                log.error("like flush failed, member={}", member, ex);
            }
        }
        if (!members.isEmpty()) log.info("like flush batch, requested={}, saved={}", members.size(), saved);
    }

    public boolean flushLikeTarget(String member) throws InterruptedException {
        String[] parts = member.split(":", 2);
        if (parts.length != 2) throw new IllegalArgumentException("invalid like dirty member");
        String targetType = LikeService.normalizeTargetType(parts[0]);
        long targetId = Long.parseLong(parts[1]);
        RLock lock = redissonClient.getLock(LikeKeys.flushLock(targetType, targetId));
        if (!lock.tryLock(3, TimeUnit.SECONDS)) return false;
        try {
            String revision = stringRedisTemplate.opsForValue().get(LikeKeys.revision(targetType, targetId));
            if (revision == null) throw new IllegalStateException("missing like revision");
            Set<String> members = stringRedisTemplate.opsForSet().members(LikeKeys.users(targetType, targetId));
            Set<String> snapshot = members == null ? Set.of() : Set.copyOf(members);
            transactionTemplate.executeWithoutResult(status -> synchronizeLikes(targetType, targetId, snapshot));
            Long removed = stringRedisTemplate.execute(acknowledgeDirtyScript, List.of(LikeKeys.revision(targetType, targetId), LikeKeys.dirty()),
                    revision, member);
            if (removed == null || removed == 0) {
                log.warn("old like revision retained dirty, targetType={}, targetId={}", targetType, targetId);
                return false;
            }
            return true;
        } finally {
            if (lock.isHeldByCurrentThread()) lock.unlock();
        }
    }

    private void synchronizeLikes(String targetType, long targetId, Set<String> desiredUserIds) {
        Set<String> currentUserIds = communityMapper.selectActiveLikers(targetType, targetId);
        for (String userId : desiredUserIds) {
            if (!currentUserIds.contains(userId)) communityMapper.upsertActiveLike(Long.parseLong(userId), targetType, targetId);
        }
        for (String userId : currentUserIds) {
            if (!desiredUserIds.contains(userId)) communityMapper.deactivateLike(Long.parseLong(userId), targetType, targetId);
        }
        int updated = "QUESTION".equals(targetType)
                ? communityMapper.updateQuestionLikeCount(targetId, desiredUserIds.size())
                : communityMapper.updateAnswerLikeCount(targetId, desiredUserIds.size());
        if (updated != 1) throw new IllegalStateException("like count update failed");
    }

    private CommunityException likeRedisUnavailable(String targetType, long targetId, long userId, Exception ex) {
        log.error("like Redis unavailable, traceId={}, userId={}, targetType={}, targetId={}",
                UserContext.traceId(), userId, targetType, targetId, ex);
        return new CommunityException(503, "点赞服务暂时不可用");
    }

    private static DefaultRedisScript<Long> loadScript(String location) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(location));
        script.setResultType(Long.class);
        return script;
    }
}
