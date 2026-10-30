package com.campustrain.growthservice.service.impl;

import com.campustrain.growthservice.dto.LearningCompletedEvent;
import com.campustrain.growthservice.dto.CommunityActionEvent;
import com.campustrain.growthservice.entity.UserPoints;
import com.campustrain.growthservice.mapper.GrowthMapper;
import com.campustrain.growthservice.service.GrowthService;
import com.campustrain.growthservice.vo.RankView;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GrowthServiceImplTest {
    private final GrowthMapper mapper = mock(GrowthMapper.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);

    @SuppressWarnings("unchecked")
    private GrowthServiceImpl service() {
        ZSetOperations<String, String> zset = mock(ZSetOperations.class);
        when(redis.opsForZSet()).thenReturn(zset);
        return new GrowthServiceImpl(mapper, redis, 10);
    }

    @Test
    void awardWritesFlowAccountAndBothRankings() {
        GrowthServiceImpl service = service();
        LearningCompletedEvent event = event("first");
        assertTrue(service.award(event));
        verify(mapper).insertFlow(eq(event), eq(10), eq(GrowthServiceImpl.shanghaiDateTime(event.occurredAt())));
        verify(mapper).incrementAccount(1001, 10);
        verify(redis.opsForZSet()).incrementScore(GrowthService.TOTAL_RANKING_KEY, "1001", 10);
        verify(redis.opsForZSet()).incrementScore(GrowthService.monthRankingKey("2026-10"), "1001", 10);
    }

    @Test
    void communityQuestionAndAnswerUseIndependentConfiguredPoints() {
        GrowthServiceImpl service = new GrowthServiceImpl(mapper, redis, 10, 2, 5);
        when(redis.opsForZSet()).thenReturn(mock(ZSetOperations.class));
        CommunityActionEvent question = new CommunityActionEvent("question-event", "COMMUNITY_ACTION", 1001L,
                18001L, "QUESTION", "QUESTION_CREATED", "2026-10-30T10:00:00+08:00", "trace");
        CommunityActionEvent answer = new CommunityActionEvent("answer-event", "COMMUNITY_ACTION", 1001L,
                18002L, "ANSWER", "ANSWER_CREATED", "2026-10-30T10:00:00+08:00", "trace");
        assertTrue(service.awardCommunity(question));
        assertTrue(service.awardCommunity(answer));
        verify(mapper).insertCommunityFlow(eq(question), eq(2), any());
        verify(mapper).insertCommunityFlow(eq(answer), eq(5), any());
        verify(mapper).incrementAccount(1001L, 2);
        verify(mapper).incrementAccount(1001L, 5);
    }

    @Test
    void duplicateCommunityEventDoesNotIncreaseAccount() {
        GrowthServiceImpl service = new GrowthServiceImpl(mapper, redis, 10, 2, 5);
        CommunityActionEvent event = new CommunityActionEvent("duplicate", "COMMUNITY_ACTION", 1001L,
                18001L, "QUESTION", "QUESTION_CREATED", "2026-10-30T10:00:00+08:00", "trace");
        doThrow(new DuplicateKeyException("duplicate")).when(mapper).insertCommunityFlow(eq(event), eq(2), any());
        assertFalse(service.awardCommunity(event));
        verify(mapper, never()).incrementAccount(anyLong(), anyInt());
    }

    @Test
    void awardRemainsTransactional() throws Exception {
        assertTrue(GrowthServiceImpl.class.getMethod("award", LearningCompletedEvent.class).isAnnotationPresent(Transactional.class));
        assertTrue(GrowthServiceImpl.class.getMethod("settle", String.class).isAnnotationPresent(Transactional.class));
    }

    @Test
    void duplicateDoesNotIncrementAccountOrRanking() {
        GrowthServiceImpl service = service();
        doThrow(new DuplicateKeyException("duplicate")).when(mapper).insertFlow(any(), anyInt(), any());
        assertFalse(service.award(event("duplicate")));
        verify(mapper, never()).incrementAccount(anyLong(), anyInt());
        verify(redis.opsForZSet(), never()).incrementScore(anyString(), anyString(), anyDouble());
    }

    @Test
    void monthUsesShanghaiForOffsetAndStage16LocalTime() {
        assertEquals("2026-11", GrowthServiceImpl.monthOf("2026-10-31T18:00:00Z"));
        assertEquals("2026-10", GrowthServiceImpl.monthOf("2026-10-28T18:31:27.192666700"));
    }

    @Test
    void topAndMineReturnOneBasedRanks() {
        GrowthServiceImpl service = service();
        when(redis.opsForZSet().reverseRange(GrowthService.TOTAL_RANKING_KEY, 0, 1))
                .thenReturn(new LinkedHashSet<>(List.of("17001", "17002")));
        when(redis.opsForZSet().score(GrowthService.TOTAL_RANKING_KEY, "17001")).thenReturn(30.0);
        when(redis.opsForZSet().score(GrowthService.TOTAL_RANKING_KEY, "17002")).thenReturn(20.0);
        when(redis.opsForZSet().reverseRank(GrowthService.TOTAL_RANKING_KEY, "17002")).thenReturn(1L);
        when(redis.opsForZSet().reverseRank(GrowthService.TOTAL_RANKING_KEY, "17003")).thenReturn(null);
        when(redis.opsForZSet().score(GrowthService.TOTAL_RANKING_KEY, "17003")).thenReturn(null);
        assertEquals(List.of(new RankView(1, 17001, 30), new RankView(2, 17002, 20)), service.top(GrowthService.TOTAL_RANKING_KEY, 2));
        assertEquals(new RankView(2, 17002, 20), service.mine(GrowthService.TOTAL_RANKING_KEY, 17002));
        assertNull(service.mine(GrowthService.TOTAL_RANKING_KEY, 17003));
    }

    @Test
    void redisFailureKeepsSuccessfulMysqlAward() {
        GrowthServiceImpl service = service();
        ZSetOperations<String, String> zset = redis.opsForZSet();
        doThrow(new IllegalStateException("redis down")).when(zset)
                .incrementScore(GrowthService.TOTAL_RANKING_KEY, "1001", 10);
        assertTrue(service.award(event("redis-down")));
        verify(mapper).incrementAccount(1001, 10);
    }

    @Test
    void rebuildUsesAbsoluteMysqlScores() {
        GrowthServiceImpl service = service();
        when(mapper.totalPoints()).thenReturn(List.of(new UserPoints(17001, 30)));
        when(mapper.monthlyPoints("2026-10")).thenReturn(List.of(new UserPoints(17001, 20)));
        service.rebuild("2026-10");
        verify(redis).delete(GrowthService.TOTAL_RANKING_KEY);
        verify(redis).delete(GrowthService.monthRankingKey("2026-10"));
        verify(redis.opsForZSet()).add(GrowthService.TOTAL_RANKING_KEY, "17001", 30.0);
        verify(redis.opsForZSet()).add(GrowthService.monthRankingKey("2026-10"), "17001", 20.0);
    }

    @Test
    void settlementWritesStableRanksAndNextSeason() {
        GrowthServiceImpl service = service();
        when(mapper.lockedSeasonId("2026-10")).thenReturn(7L);
        when(mapper.seasonStatus(7L)).thenReturn("ACTIVE");
        when(mapper.seasonPoints("2026-10")).thenReturn(List.of(new UserPoints(17001, 30), new UserPoints(17002, 20)));
        service.settle("2026-10");
        verify(mapper).insertHistory(7, "2026-10", 17001, 30, 1);
        verify(mapper).insertHistory(7, "2026-10", 17002, 20, 2);
        verify(mapper).settleSeason(7);
        verify(mapper).ensureSeason("2026-11");
        verify(mapper).recordExecution("2026-10", "DONE");
        verify(redis, never()).delete(GrowthService.TOTAL_RANKING_KEY);
    }

    @Test
    void settledSeasonDoesNotWriteHistoryAgain() {
        GrowthServiceImpl service = service();
        when(mapper.lockedSeasonId("2026-10")).thenReturn(7L);
        when(mapper.seasonStatus(7L)).thenReturn("SETTLED");
        service.settle("2026-10");
        verify(mapper, never()).insertHistory(anyLong(), anyString(), anyLong(), anyLong(), anyLong());
        verify(mapper).recordExecution("2026-10", "DONE");
    }

    private LearningCompletedEvent event(String id) {
        return new LearningCompletedEvent(id, "LEARNING_COMPLETED", 1001L, 1L, 2L, 3L,
                "2026-10-20T10:00:00+08:00", "trace-test");
    }
}
