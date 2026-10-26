package com.campustrain.learningservice;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

@EnabledIfEnvironmentVariable(named = "STAGE16_REDIS_INTEGRATION", matches = "true")
class ProgressLuaIntegrationTest {
    private LettuceConnectionFactory factory;
    private StringRedisTemplate redis;
    private String key;
    private String dirty;
    private DefaultRedisScript<Long> initialize;
    @SuppressWarnings("rawtypes") private DefaultRedisScript<List> update;
    private DefaultRedisScript<Long> clear;
    private DefaultRedisScript<Long> complete;

    private static <T> DefaultRedisScript<T> script(String path, Class<T> type) {
        DefaultRedisScript<T> s = new DefaultRedisScript<>(); s.setLocation(new ClassPathResource(path)); s.setResultType(type); return s;
    }
    @BeforeEach void setup() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(System.getenv().getOrDefault("REDIS_HOST", "192.168.10.102"),
                Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6379")));
        config.setPassword(System.getenv().getOrDefault("REDIS_PASSWORD", "123456"));
        factory = new LettuceConnectionFactory(config); factory.afterPropertiesSet(); factory.start();
        redis = new StringRedisTemplate(factory); redis.afterPropertiesSet();
        String prefix = "campustrain:stage16:test:" + UUID.randomUUID(); key = prefix + ":progress"; dirty = prefix + ":dirty";
        initialize = script("lua/initialize.lua", Long.class); update = script("lua/progress.lua", List.class);
        clear = script("lua/clear-dirty.lua", Long.class); complete = script("lua/complete.lua", Long.class);
        assertEquals(1L, redis.execute(initialize, List.of(key, dirty), "1", "2", "3", "4", "5", "0", "100", "0", "time", "trace", "1", "2026-01-01T00:00:00"));
    }
    @AfterEach void tearDown() { redis.delete(List.of(key, dirty)); factory.destroy(); }
    private List<?> update(int seconds) { return redis.execute(update, List.of(key, dirty), "1:2", Integer.toString(seconds), "90", "time", "trace"); }

    @Test void outOfOrderAndCompletionAreMonotonic() {
        assertEquals(1L, update(10).get(0)); assertEquals(1L, update(30).get(0)); assertEquals(0L, update(20).get(0));
        assertEquals("30", redis.opsForHash().get(key, "progressSeconds"));
        assertEquals(2L, update(90).get(0)); assertEquals(1L, update(95).get(0));
        assertEquals("1", redis.opsForHash().get(key, "completed"));
    }
    @Test void staleFlushCannotClearNewerDirtyVersion() {
        update(10); String version = (String) redis.opsForHash().get(key, "revision"); update(20);
        assertEquals(0L, redis.execute(clear, List.of(key, dirty), "1:2", version));
        assertTrue(Boolean.TRUE.equals(redis.opsForSet().isMember(dirty, "1:2")));
        assertEquals(1L, redis.execute(clear, List.of(key, dirty), "1:2", (String) redis.opsForHash().get(key, "revision")));
    }
    @Test void concurrentUpdatesKeepMaximumAndTextCompletionIsIdempotent() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(12);
        try { for (int n : IntStream.rangeClosed(1, 100).toArray()) pool.submit(() -> update(n)); }
        finally { pool.shutdown(); assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS)); }
        assertEquals("100", redis.opsForHash().get(key, "progressSeconds"));
        assertEquals(0L, redis.execute(complete, List.of(key, dirty), "1:2", "time", "trace"));
    }
}
