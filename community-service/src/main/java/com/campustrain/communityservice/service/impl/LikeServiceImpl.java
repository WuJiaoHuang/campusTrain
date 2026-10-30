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
    private final CommunityMapper mapper;
    private final StringRedisTemplate redis;
    private final RedissonClient redisson;
    private final TransactionTemplate transactions;
    private final int batchSize;
    private final DefaultRedisScript<Long> initialize = script("lua/like-initialize.lua");
    private final DefaultRedisScript<Long> add = script("lua/like-add.lua");
    private final DefaultRedisScript<Long> remove = script("lua/like-remove.lua");
    private final DefaultRedisScript<Long> ackDirty = script("lua/like-ack-dirty.lua");

    public LikeServiceImpl(CommunityMapper mapper, StringRedisTemplate redis, RedissonClient redisson,
                           TransactionTemplate transactions,
                           @Value("${campustrain.community.like.flush-batch-size:100}") int batchSize) {
        this.mapper = mapper; this.redis = redis; this.redisson = redisson;
        this.transactions = transactions; this.batchSize = batchSize;
    }

    @Override
    public LikeView like(String type, long targetId) { return change(type, targetId, true); }

    @Override
    public LikeView unlike(String type, long targetId) { return change(type, targetId, false); }

    private LikeView change(String rawType, long targetId, boolean like) {
        String type = LikeService.targetType(rawType);
        long userId = userId();
        long persisted = persistedCount(type, targetId);
        try {
            ensureInitialized(type, targetId);
            Long changed = redis.execute(like ? add : remove, keys(type, targetId),
                    String.valueOf(userId), LikeKeys.member(type, targetId));
            if (changed == null || changed < 0) throw new IllegalStateException("like state not initialized");
            if (changed == 0) log.warn("repeat like operation, traceId={}, userId={}, targetType={}, targetId={}, like={}",
                    UserContext.traceId(), userId, type, targetId, like);
            LikeView state = state(type, targetId, userId);
            log.debug("like operation, traceId={}, userId={}, targetType={}, targetId={}, like={}, changed={}",
                    UserContext.traceId(), userId, type, targetId, like, changed);
            return new LikeView(type, targetId, state.likeCount(), state.likedByMe(), changed == 1);
        } catch (CommunityException ex) { throw ex; }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw redisUnavailable(type, targetId, userId, ex);
        }
        catch (Exception ex) { throw redisUnavailable(type, targetId, userId, ex); }
    }

    @Override
    public LikeView read(String rawType, long targetId, long persistedCount) {
        String type = LikeService.targetType(rawType);
        Long userId = UserContext.current() == null ? null : UserContext.current().userId();
        try {
            ensureInitialized(type, targetId);
            return state(type, targetId, userId);
        } catch (Exception ex) {
            log.warn("like read Redis unavailable; using MySQL, traceId={}, targetType={}, targetId={}",
                    UserContext.traceId(), type, targetId, ex);
            return new LikeView(type, targetId, persistedCount,
                    userId != null && mapper.activeLikers(type, targetId).contains(String.valueOf(userId)), false);
        }
    }

    @Override
    public LikeView readCurrent(String type, long targetId) {
        type = LikeService.targetType(type);
        return read(type, targetId, persistedCount(type, targetId));
    }

    @Override
    public Map<Long, LikeView> readBatch(String rawType, Map<Long, Long> persistedCounts) {
        String type = LikeService.targetType(rawType);
        if (persistedCounts.isEmpty()) return Map.of();
        Long userId = UserContext.current() == null ? null : UserContext.current().userId();
        List<Long> ids = new ArrayList<>(persistedCounts.keySet());
        try {
            List<Object> values = batchValues(type, ids, userId);
            for (int i = 0; i < ids.size(); i++) {
                if (values.get(i * 2) == null) ensureInitialized(type, ids.get(i));
            }
            if (values.stream().anyMatch(java.util.Objects::isNull)) values = batchValues(type, ids, userId);
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
            Set<Long> liked = userId == null ? Set.of() : mapper.likedTargets(type, new HashSet<>(ids), userId);
            Map<Long, LikeView> result = new LinkedHashMap<>();
            persistedCounts.forEach((id, count) -> result.put(id, new LikeView(type, id, count, liked.contains(id), false)));
            return result;
        }
    }

    private List<Object> batchValues(String type, List<Long> ids, Long userId) {
        return redis.executePipelined((RedisCallback<Object>) connection -> {
            var serializer = redis.getStringSerializer();
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

    private LikeView state(String type, long targetId, Long userId) {
        String count = redis.opsForValue().get(LikeKeys.count(type, targetId));
        Boolean liked = userId == null ? false : redis.opsForSet().isMember(LikeKeys.users(type, targetId), String.valueOf(userId));
        return new LikeView(type, targetId, count == null ? 0 : Long.parseLong(count), Boolean.TRUE.equals(liked), false);
    }

    private long persistedCount(String type, long targetId) {
        Question question = "QUESTION".equals(type) ? mapper.question(targetId) : null;
        Answer answer = "ANSWER".equals(type) ? mapper.answer(targetId) : null;
        if (question == null && answer == null) throw new CommunityException(404, "点赞目标不存在");
        return question != null ? question.likeCount() : answer.likeCount();
    }

    private long userId() {
        if (UserContext.current() == null || UserContext.current().userId() == null)
            throw new CommunityException(401, "用户身份缺失");
        return UserContext.current().userId();
    }

    private List<String> keys(String type, long targetId) {
        return List.of(LikeKeys.users(type, targetId), LikeKeys.count(type, targetId),
                LikeKeys.revision(type, targetId), LikeKeys.initialized(type, targetId), LikeKeys.dirty());
    }

    private void ensureInitialized(String type, long targetId) throws InterruptedException {
        if (Boolean.TRUE.equals(redis.hasKey(LikeKeys.initialized(type, targetId)))) return;
        RLock lock = redisson.getLock(LikeKeys.lock(type, targetId));
        boolean acquired = lock.tryLock(3, TimeUnit.SECONDS);
        if (!acquired) throw new IllegalStateException("like initialization lock timed out");
        try {
            if (Boolean.TRUE.equals(redis.hasKey(LikeKeys.initialized(type, targetId)))) return;
            Set<String> persistedUsers = mapper.activeLikers(type, targetId);
            redis.execute(initialize, keys(type, targetId).subList(0, 4), persistedUsers.toArray(String[]::new));
            log.info("like state initialized, traceId={}, targetType={}, targetId={}, users={}",
                    UserContext.traceId(), type, targetId, persistedUsers.size());
        } finally {
            if (lock.isHeldByCurrentThread()) lock.unlock();
        }
    }

    @Scheduled(fixedDelayString = "${campustrain.community.like.flush-interval-ms:5000}")
    public void flush() {
        List<String> members = new ArrayList<>();
        try (Cursor<String> cursor = redis.opsForSet().scan(LikeKeys.dirty(),
                ScanOptions.scanOptions().count(batchSize).build())) {
            while (cursor.hasNext() && members.size() < batchSize) members.add(cursor.next());
        } catch (Exception ex) {
            log.error("like dirty scan failed", ex);
            return;
        }
        int saved = 0;
        for (String member : members) {
            try { if (flushOne(member)) saved++; }
            catch (Exception ex) { log.error("like flush failed, member={}", member, ex); }
        }
        if (!members.isEmpty()) log.info("like flush batch, requested={}, saved={}", members.size(), saved);
    }

    public boolean flushOne(String member) throws InterruptedException {
        String[] parts = member.split(":", 2);
        if (parts.length != 2) throw new IllegalArgumentException("invalid like dirty member");
        String type = LikeService.targetType(parts[0]);
        long targetId = Long.parseLong(parts[1]);
        RLock lock = redisson.getLock(LikeKeys.flushLock(type, targetId));
        if (!lock.tryLock(3, TimeUnit.SECONDS)) return false;
        try {
            String revision = redis.opsForValue().get(LikeKeys.revision(type, targetId));
            if (revision == null) throw new IllegalStateException("missing like revision");
            Set<String> members = redis.opsForSet().members(LikeKeys.users(type, targetId));
            Set<String> snapshot = members == null ? Set.of() : Set.copyOf(members);
            transactions.executeWithoutResult(status -> mapper.synchronizeLikes(type, targetId, snapshot));
            Long removed = redis.execute(ackDirty, List.of(LikeKeys.revision(type, targetId), LikeKeys.dirty()),
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

    private CommunityException redisUnavailable(String type, long id, long userId, Exception ex) {
        log.error("like Redis unavailable, traceId={}, userId={}, targetType={}, targetId={}",
                UserContext.traceId(), userId, type, id, ex);
        return new CommunityException(503, "点赞服务暂时不可用");
    }

    private static DefaultRedisScript<Long> script(String location) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(location));
        script.setResultType(Long.class);
        return script;
    }
}
