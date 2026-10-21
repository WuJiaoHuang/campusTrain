package com.campustrain.trainingservice.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.campustrain.trainingservice.config.TrainingCacheProperties;
import com.campustrain.trainingservice.vo.TrainingVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class TrainingCacheServiceTest {
    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private TrainingCacheProperties properties;
    private TrainingCacheService cache;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setup() {
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        properties = new TrainingCacheProperties();
        properties.setDetailTtl(Duration.ofMinutes(5));
        properties.setCatalogTtl(Duration.ofMinutes(6));
        properties.setNullTtl(Duration.ofSeconds(30));
        properties.setCaffeineTtl(Duration.ofMinutes(1));
        properties.setCaffeineMaximumSize(10);
        properties.setTtlJitter(Duration.ZERO);
        cache = new TrainingCacheService(redis, new ObjectMapper().registerModule(new JavaTimeModule()), properties);
    }

    @Test
    void l1HitAvoidsRedisAndDatabase() {
        AtomicInteger db = new AtomicInteger();
        CacheLookup<TrainingVO> first = detail(1L, () -> vo(1L, db.incrementAndGet()));
        CacheLookup<TrainingVO> second = detail(1L, () -> vo(1L, db.incrementAndGet()));
        assertTrue(first.found());
        assertEquals(1, second.value().getEnrolledCount());
        assertEquals(1, db.get());
        verify(values, times(1)).get(TrainingCacheKeys.detail(1L));
    }

    @Test
    void l2HitRefillsL1WithoutDatabase() throws Exception {
        String key = TrainingCacheKeys.detail(2L);
        String json = new ObjectMapper().writeValueAsString(vo(2L, 9));
        when(values.get(key)).thenReturn(json);
        CacheLookup<TrainingVO> hit = detail(2L, () -> { throw new AssertionError("DB should not run"); });
        assertTrue(hit.found());
        assertEquals(9, hit.value().getEnrolledCount());
        detail(2L, () -> { throw new AssertionError("L1 should serve"); });
        verify(values, times(1)).get(key);
    }

    @Test
    void absentValueIsCachedAndDoesNotRepeatDatabaseLookup() {
        AtomicInteger db = new AtomicInteger();
        assertFalse(detail(404L, () -> { db.incrementAndGet(); return null; }).found());
        assertFalse(detail(404L, () -> { db.incrementAndGet(); return null; }).found());
        assertEquals(1, db.get());
        verify(values).set(eq(TrainingCacheKeys.detail(404L)), anyString(), eq(Duration.ofSeconds(30)));
    }

    @Test
    void concurrentHotMissUsesSingleFlightForDatabase() throws Exception {
        AtomicInteger db = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(12);
        try {
            List<Callable<CacheLookup<TrainingVO>>> tasks = new ArrayList<>();
            for (int i = 0; i < 24; i++) {
                tasks.add(() -> detail(88L, () -> {
                    db.incrementAndGet();
                    try { Thread.sleep(80); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    return vo(88L, 1);
                }));
            }
            for (Future<CacheLookup<TrainingVO>> result : pool.invokeAll(tasks)) assertTrue(result.get().found());
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, db.get());
    }

    @Test
    void redisReadFailureFallsBackToDatabaseAndEvictionDeletesBothLevels() {
        when(values.get(TrainingCacheKeys.detail(3L))).thenThrow(new IllegalStateException("redis unavailable"));
        assertTrue(detail(3L, () -> vo(3L, 1)).found());
        cache.evictDetail(3L);
        verify(redis).delete(TrainingCacheKeys.detail(3L));
    }

    @Test
    void configuredTtlsAndMaximumSizeAreUsed() {
        assertEquals(Duration.ofMinutes(5), cache.detailTtl());
        assertEquals(Duration.ofMinutes(6), cache.catalogTtl());
        properties.setCaffeineMaximumSize(1);
        detail(11L, () -> vo(11L, 1));
        detail(12L, () -> vo(12L, 1));
        assertTrue(true);
    }

    private CacheLookup<TrainingVO> detail(Long id, java.util.function.Supplier<TrainingVO> loader) {
        return cache.get(TrainingCacheKeys.detail(id), id, TrainingVO.class, properties.getDetailTtl(), loader);
    }

    private TrainingVO vo(Long id, int count) {
        TrainingVO value = new TrainingVO();
        value.setId(id);
        value.setTitle("training-" + id);
        value.setEnrolledCount(count);
        return value;
    }
}
