package com.campustrain.growthservice;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.sql.ResultSet;
import org.springframework.transaction.annotation.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.PreparedStatementSetter;

class GrowthServiceTest {

    @SuppressWarnings("unchecked")
    private GrowthService service(JdbcTemplate jdbc, StringRedisTemplate redis) {
        ZSetOperations<String, String> zset = mock(ZSetOperations.class);
        when(redis.opsForZSet()).thenReturn(zset);
        return new GrowthService(jdbc, redis, 10);
    }

    @Test
    void learningCompletionPersistsPointsAndUpdatesTotalAndMonthlyRankings() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        GrowthService service = service(jdbc, redis);
        LearningCompletedEvent event = event("event-1", 1001L, "2026-10-20T10:00:00+08:00");

        assertTrue(service.award(event));

        verify(jdbc).update(anyString(), any(), any(), any(), any(), any(), any());
        verify(jdbc).update(startsWith("INSERT INTO points_account"), eq(1001L), eq(10));
        verify(redis.opsForZSet()).incrementScore(GrowthService.TOTAL_RANKING_KEY, "1001", 10);
        verify(redis.opsForZSet()).incrementScore(GrowthService.monthRankingKey("2026-10"), "1001", 10);
    }

    @Test
    void accountAndFlowAreInOneLocalTransaction() throws Exception {
        assertTrue(GrowthService.class.getMethod("award", LearningCompletedEvent.class)
                .isAnnotationPresent(Transactional.class));
    }

    @Test
    void duplicateEventDoesNotUpdateAccountOrRedisAgain() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        doThrow(new DuplicateKeyException("duplicate")).when(jdbc).update(anyString(), any(), any(), any(), any(), any(), any());
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        GrowthService service = service(jdbc, redis);

        assertFalse(service.award(event("event-duplicate", 1001L, "2026-10-20T10:00:00+08:00")));

        verify(redis.opsForZSet(), org.mockito.Mockito.never()).incrementScore(anyString(), anyString(), anyDouble());
    }

    @Test
    void convertsEventTimeToShanghaiMonth() {
        assertEquals("2026-11", GrowthService.monthOf("2026-10-31T18:00:00Z"));
        assertEquals("2026-10", GrowthService.monthOf("2026-10-28T18:31:27.192666700"));
    }

    @Test
    void topNAndPersonalRankUseOneBasedRankAndRealScores() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        GrowthService service = service(jdbc, redis);
        when(redis.opsForZSet().reverseRange(GrowthService.TOTAL_RANKING_KEY, 0, 1))
                .thenReturn(new LinkedHashSet<>(Set.of("17001", "17002")));
        when(redis.opsForZSet().score(GrowthService.TOTAL_RANKING_KEY, "17001")).thenReturn(30.0);
        when(redis.opsForZSet().score(GrowthService.TOTAL_RANKING_KEY, "17002")).thenReturn(20.0);
        when(redis.opsForZSet().reverseRank(GrowthService.TOTAL_RANKING_KEY, "17002")).thenReturn(1L);
        when(redis.opsForZSet().reverseRank(GrowthService.TOTAL_RANKING_KEY, "17003")).thenReturn(null);
        when(redis.opsForZSet().score(GrowthService.TOTAL_RANKING_KEY, "17003")).thenReturn(null);

        assertEquals(2, service.top(GrowthService.TOTAL_RANKING_KEY, 2).size());
        assertEquals(new RankView(2, 17002, 20), service.mine(GrowthService.TOTAL_RANKING_KEY, 17002));
        assertNull(service.mine(GrowthService.TOTAL_RANKING_KEY, 17003));
    }

    @Test
    void redisFailureDoesNotTurnSuccessfulDbWriteIntoMessageRetry() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        GrowthService service = service(jdbc, redis);
        ZSetOperations<String, String> zset = redis.opsForZSet();
        org.mockito.Mockito.doThrow(new IllegalStateException("redis down"))
                .when(zset).incrementScore(GrowthService.TOTAL_RANKING_KEY, "1001", 10);
        assertTrue(service.award(event("redis-down", 1001L, "2026-10-20T10:00:00+08:00")));
        verify(jdbc).update(anyString(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void rebuildUsesAbsoluteMysqlScoresForTotalAndMonthlyRanking() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        GrowthService service = service(jdbc, redis);
        ResultSet row = mock(ResultSet.class);
        when(row.getLong("user_id")).thenReturn(17001L);
        when(row.getDouble("total_points")).thenReturn(30.0);
        when(row.getDouble("points")).thenReturn(20.0);
        org.mockito.Mockito.doAnswer(call -> {
            ((RowCallbackHandler) call.getArgument(1)).processRow(row);
            return null;
        }).when(jdbc).query(startsWith("SELECT user_id,total_points"), any(RowCallbackHandler.class));
        org.mockito.Mockito.doAnswer(call -> {
            ((RowCallbackHandler) call.getArgument(2)).processRow(row);
            return null;
        }).when(jdbc).query(startsWith("SELECT user_id,SUM(points)"), any(PreparedStatementSetter.class), any(RowCallbackHandler.class));

        service.rebuild("2026-10");

        verify(redis).delete(GrowthService.TOTAL_RANKING_KEY);
        verify(redis).delete(GrowthService.monthRankingKey("2026-10"));
        verify(redis.opsForZSet()).add(GrowthService.TOTAL_RANKING_KEY, "17001", 30.0);
        verify(redis.opsForZSet()).add(GrowthService.monthRankingKey("2026-10"), "17001", 20.0);
    }

    @Test
    void settlementRecordsStableRanksAndNextActiveSeason() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        GrowthService service = service(jdbc, redis);
        when(jdbc.queryForObject(startsWith("SELECT id FROM ranking_season"), eq(Long.class), eq("2026-10"))).thenReturn(7L);
        when(jdbc.queryForObject(startsWith("SELECT status FROM ranking_season"), eq(String.class), eq(7L))).thenReturn("ACTIVE");
        ResultSet first = mock(ResultSet.class);
        when(first.getLong("user_id")).thenReturn(17001L);
        when(first.getLong("points")).thenReturn(30L);
        ResultSet second = mock(ResultSet.class);
        when(second.getLong("user_id")).thenReturn(17002L);
        when(second.getLong("points")).thenReturn(20L);
        org.mockito.Mockito.doAnswer(call -> {
            RowCallbackHandler callback = call.getArgument(2);
            callback.processRow(first);
            callback.processRow(second);
            return null;
        }).when(jdbc).query(startsWith("SELECT user_id,SUM(points)"), any(PreparedStatementSetter.class), any(RowCallbackHandler.class));

        service.settle("2026-10");

        verify(jdbc).update(startsWith("INSERT IGNORE INTO ranking_history"), eq(7L), eq("2026-10"), eq(17001L), eq(30L), eq(1L));
        verify(jdbc).update(startsWith("INSERT IGNORE INTO ranking_history"), eq(7L), eq("2026-10"), eq(17002L), eq(20L), eq(2L));
        verify(jdbc).update(startsWith("UPDATE ranking_season SET status='SETTLED'"), eq(7L));
        verify(jdbc).update(startsWith("INSERT IGNORE INTO ranking_season"), eq("2026-11"), eq("2026-11"), eq("2026-11"));
        verify(redis, never()).delete(GrowthService.TOTAL_RANKING_KEY);
    }

    @Test
    void settledSeasonDoesNotGenerateAnotherHistorySnapshot() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        GrowthService service = service(jdbc, redis);
        when(jdbc.queryForObject(startsWith("SELECT id FROM ranking_season"), eq(Long.class), eq("2026-10"))).thenReturn(7L);
        when(jdbc.queryForObject(startsWith("SELECT status FROM ranking_season"), eq(String.class), eq(7L))).thenReturn("SETTLED");

        service.settle("2026-10");

        verify(jdbc, never()).update(startsWith("INSERT IGNORE INTO ranking_history"), any(), any(), any(), any(), any());
        verify(jdbc).update(startsWith("INSERT INTO job_execution_record"), eq("2026-10"), eq("DONE"), eq("DONE"));
    }

    private LearningCompletedEvent event(String eventId, long userId, String occurredAt) {
        return new LearningCompletedEvent(eventId, "LEARNING_COMPLETED", userId, 1L, 2L, 3L,
                occurredAt, "trace-test");
    }
}
