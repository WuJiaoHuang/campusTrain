package com.campustrain.communityservice.context;

import com.campustrain.communityservice.config.LikeKeys;
import com.campustrain.communityservice.service.LikeService;
import com.campustrain.communityservice.service.impl.LikeServiceImpl;
import com.campustrain.communityservice.vo.LikeView;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.sql.Statement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "STAGE18_LIVE_INTEGRATION", matches = "true")
class Stage18LikeIntegrationTest {
    @Autowired LikeService likes;
    @Autowired LikeServiceImpl flush;
    @Autowired StringRedisTemplate redis;
    @Autowired JdbcTemplate jdbc;
    long targetId;

    @BeforeEach
    void createTarget() {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> connection.prepareStatement(
                "INSERT INTO question(training_id,user_id,title,content,answer_count,comment_count,like_count,status,create_time,update_time) VALUES(2003,1001,'stage18-live-test','test',0,0,0,1,NOW(),NOW())",
                Statement.RETURN_GENERATED_KEYS), key);
        targetId = key.getKey().longValue();
        UserContext.set(new UserIdentity(1001L, "STUDENT", "student01", "test-trace"));
    }

    @AfterEach
    void cleanup() {
        UserContext.clear();
        redis.delete(List.of(LikeKeys.users("QUESTION", targetId), LikeKeys.count("QUESTION", targetId),
                LikeKeys.revision("QUESTION", targetId), LikeKeys.initialized("QUESTION", targetId),
                LikeKeys.lock("QUESTION", targetId), LikeKeys.flushLock("QUESTION", targetId)));
        redis.opsForSet().remove(LikeKeys.dirty(), LikeKeys.member("QUESTION", targetId));
        jdbc.update("DELETE FROM like_relation WHERE target_type='QUESTION' AND target_id=?", targetId);
        jdbc.update("DELETE FROM question WHERE id=? AND title='stage18-live-test'", targetId);
    }

    @Test
    void batchReadReturnsLiveLikeCountAndCurrentUserState() {
        likes.like("QUESTION", targetId);
        LikeView first = likes.readBatch("QUESTION", Map.of(targetId, 0L)).get(targetId);
        assertEquals(1L, first.likeCount());
        assertTrue(first.likedByMe());
        UserContext.set(new UserIdentity(1002L, "STUDENT", "student02", "test-trace"));
        LikeView second = likes.readBatch("QUESTION", Map.of(targetId, 0L)).get(targetId);
        assertEquals(1L, second.likeCount());
        assertFalse(second.likedByMe());
    }

    @Test
    void likeIsAtomicAndFlushRecoversFromMysql() throws Exception {
        LikeView first = likes.like("QUESTION", targetId);
        assertTrue(first.changed());
        assertEquals(1, first.likeCount());
        assertFalse(likes.like("QUESTION", targetId).changed());
        assertEquals(1, likes.readCurrent("QUESTION", targetId).likeCount());
        assertTrue(Boolean.TRUE.equals(redis.opsForSet().isMember(LikeKeys.dirty(), LikeKeys.member("QUESTION", targetId))));
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM like_relation WHERE target_type='QUESTION' AND target_id=?",
                Long.class, targetId));
        assertTrue(flush.flushOne(LikeKeys.member("QUESTION", targetId)));
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM like_relation WHERE target_type='QUESTION' AND target_id=? AND status=1",
                Long.class, targetId));
        redis.delete(List.of(LikeKeys.users("QUESTION", targetId), LikeKeys.count("QUESTION", targetId),
                LikeKeys.revision("QUESTION", targetId), LikeKeys.initialized("QUESTION", targetId)));
        assertEquals(1, likes.readCurrent("QUESTION", targetId).likeCount());
        assertTrue(likes.readCurrent("QUESTION", targetId).likedByMe());
        assertTrue(likes.unlike("QUESTION", targetId).changed());
        assertFalse(likes.unlike("QUESTION", targetId).changed());
        assertEquals(0, likes.readCurrent("QUESTION", targetId).likeCount());
        assertTrue(flush.flushOne(LikeKeys.member("QUESTION", targetId)));
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM like_relation WHERE target_type='QUESTION' AND target_id=? AND status=1",
                Long.class, targetId));
        assertEquals(0L, jdbc.queryForObject("SELECT like_count FROM question WHERE id=?", Long.class, targetId));
    }

    @Test
    void concurrentDuplicateAndOldRevisionCannotClearNewDirty() throws Exception {
        var pool = Executors.newFixedThreadPool(16);
        try {
            List<Callable<Boolean>> calls = new ArrayList<>();
            for (int i = 0; i < 40; i++) calls.add(() -> {
                UserContext.set(new UserIdentity(1001L, "STUDENT", "student01", "parallel"));
                try { return likes.like("QUESTION", targetId).changed(); }
                finally { UserContext.clear(); }
            });
            int changes = 0;
            for (Future<Boolean> future : pool.invokeAll(calls)) if (future.get()) changes++;
            assertEquals(1, changes);
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, likes.readCurrent("QUESTION", targetId).likeCount());
        String oldRevision = redis.opsForValue().get(LikeKeys.revision("QUESTION", targetId));
        UserContext.set(new UserIdentity(1002L, "STUDENT", "student02", "parallel"));
        assertTrue(likes.like("QUESTION", targetId).changed());
        DefaultRedisScript<Long> ack = new DefaultRedisScript<>();
        ack.setLocation(new ClassPathResource("lua/like-ack-dirty.lua"));
        ack.setResultType(Long.class);
        assertEquals(0L, redis.execute(ack,
                List.of(LikeKeys.revision("QUESTION", targetId), LikeKeys.dirty()),
                oldRevision, LikeKeys.member("QUESTION", targetId)));
        assertTrue(Boolean.TRUE.equals(redis.opsForSet().isMember(LikeKeys.dirty(), LikeKeys.member("QUESTION", targetId))));
        assertEquals(2, likes.readCurrent("QUESTION", targetId).likeCount());
        assertTrue(flush.flushOne(LikeKeys.member("QUESTION", targetId)));
        assertEquals(2L, jdbc.queryForObject("SELECT COUNT(*) FROM like_relation WHERE target_type='QUESTION' AND target_id=? AND status=1",
                Long.class, targetId));
    }

    @Test
    void distinctUsersIncreaseCountOnceEach() throws Exception {
        var pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<LikeView>> calls = new ArrayList<>();
            for (long user = 1001; user <= 1004; user++) {
                long id = user;
                for (int repeat = 0; repeat < 5; repeat++) calls.add(() -> {
                    UserContext.set(new UserIdentity(id, "STUDENT", "student", "parallel"));
                    try { return likes.like("QUESTION", targetId); }
                    finally { UserContext.clear(); }
                });
            }
            for (Future<LikeView> future : pool.invokeAll(calls)) assertTrue(future.get().likeCount() <= 4);
        } finally {
            pool.shutdownNow();
        }
        assertEquals(4, likes.readCurrent("QUESTION", targetId).likeCount());
        assertTrue(flush.flushOne(LikeKeys.member("QUESTION", targetId)));
        assertEquals(4L, jdbc.queryForObject("SELECT COUNT(*) FROM like_relation WHERE target_type='QUESTION' AND target_id=? AND status=1",
                Long.class, targetId));
        assertEquals(4L, jdbc.queryForObject("SELECT like_count FROM question WHERE id=?", Long.class, targetId));
    }
}
