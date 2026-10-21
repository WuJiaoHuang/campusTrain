package com.campustrain.trainingservice.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.campustrain.trainingservice.config.TrainingCacheProperties;
import com.campustrain.trainingservice.context.TraceIdContext;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class TrainingCacheService {
    private static final String NULL_MARKER = "__CAMPUS_TRAIN_NULL__";
    private static final Object LOCAL_NULL_MARKER = new Object();

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final TrainingCacheProperties properties;
    private final ConcurrentHashMap<String, Object> keyLocks = new ConcurrentHashMap<>();
    private volatile Cache<String, Object> localCache;
    private volatile long localMaximumSize = -1;
    private volatile Duration localTtl;

    public TrainingCacheService(StringRedisTemplate redisTemplate, @Qualifier("trainingCacheObjectMapper") ObjectMapper objectMapper,
            TrainingCacheProperties properties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public <T> CacheLookup<T> get(String cacheKey, Long trainingId, Class<T> valueType, Duration redisTtl,
            Supplier<T> databaseLoader) {
        CacheLookup<T> local = readLocal(cacheKey, trainingId, valueType);
        if (local != null) return local;

        Object lock = keyLocks.computeIfAbsent(cacheKey, ignored -> new Object());
        synchronized (lock) {
            try {
                CacheLookup<T> doubleChecked = readLocal(cacheKey, trainingId, valueType);
                if (doubleChecked != null) return doubleChecked;

                CacheLookup<T> redis = readRedis(cacheKey, trainingId, valueType);
                if (redis != null) {
                    putLocal(cacheKey, redis);
                    return redis;
                }

                log.debug("cache DB MISS, traceId={}, trainingId={}, cacheKey={}", TraceIdContext.get(), trainingId, cacheKey);
                T value = databaseLoader.get();
                CacheLookup<T> loaded = value == null ? CacheLookup.absent() : CacheLookup.found(value);
                writeRedis(cacheKey, trainingId, loaded, redisTtl);
                putLocal(cacheKey, loaded);
                return loaded;
            } finally {
                keyLocks.remove(cacheKey, lock);
            }
        }
    }

    public void evictDetail(Long trainingId) {
        evict(TrainingCacheKeys.detail(trainingId), trainingId);
    }

    public Duration detailTtl() {
        return properties.getDetailTtl();
    }

    public Duration catalogTtl() {
        return properties.getCatalogTtl();
    }

    public void evictCatalog(Long trainingId) {
        evict(TrainingCacheKeys.catalog(trainingId), trainingId);
    }

    private <T> CacheLookup<T> readLocal(String cacheKey, Long trainingId, Class<T> valueType) {
        Object local = localCache().getIfPresent(cacheKey);
        if (local == null) return null;
        log.debug("cache L1 HIT, traceId={}, trainingId={}, cacheKey={}", TraceIdContext.get(), trainingId, cacheKey);
        return local == LOCAL_NULL_MARKER ? CacheLookup.absent() : CacheLookup.found(valueType.cast(local));
    }

    private <T> CacheLookup<T> readRedis(String cacheKey, Long trainingId, Class<T> valueType) {
        try {
            String raw = redisTemplate.opsForValue().get(cacheKey);
            if (raw == null) return null;
            log.debug("cache L2 HIT, traceId={}, trainingId={}, cacheKey={}", TraceIdContext.get(), trainingId, cacheKey);
            return NULL_MARKER.equals(raw) ? CacheLookup.absent() : CacheLookup.found(objectMapper.readValue(raw, valueType));
        } catch (Exception e) {
            log.warn("Redis cache read failed; falling back to DB, traceId={}, trainingId={}, cacheKey={}",
                    TraceIdContext.get(), trainingId, cacheKey, e);
            return null;
        }
    }

    private <T> void writeRedis(String cacheKey, Long trainingId, CacheLookup<T> value, Duration ttl) {
        try {
            String raw = value.found() ? objectMapper.writeValueAsString(value.value()) : NULL_MARKER;
            Duration effectiveTtl = value.found() ? withJitter(ttl) : properties.getNullTtl();
            redisTemplate.opsForValue().set(cacheKey, raw, effectiveTtl);
        } catch (JsonProcessingException e) {
            log.error("Redis cache serialization failed, traceId={}, trainingId={}, cacheKey={}",
                    TraceIdContext.get(), trainingId, cacheKey, e);
        } catch (Exception e) {
            log.warn("Redis cache write failed, traceId={}, trainingId={}, cacheKey={}",
                    TraceIdContext.get(), trainingId, cacheKey, e);
        }
    }

    private <T> void putLocal(String cacheKey, CacheLookup<T> value) {
        localCache().put(cacheKey, value.found() ? value.value() : LOCAL_NULL_MARKER);
    }

    private void evict(String cacheKey, Long trainingId) {
        localCache().invalidate(cacheKey);
        try {
            redisTemplate.delete(cacheKey);
        } catch (Exception e) {
            log.warn("Redis cache eviction failed, traceId={}, trainingId={}, cacheKey={}",
                    TraceIdContext.get(), trainingId, cacheKey, e);
        }
    }

    private Duration withJitter(Duration ttl) {
        long jitterSeconds = Math.max(0, properties.getTtlJitter().toSeconds());
        return ttl.plusSeconds(jitterSeconds == 0 ? 0 : ThreadLocalRandom.current().nextLong(jitterSeconds + 1));
    }

    private Cache<String, Object> localCache() {
        Duration configuredTtl = properties.getCaffeineTtl();
        long configuredMaximumSize = properties.getCaffeineMaximumSize();
        Cache<String, Object> current = localCache;
        if (current == null || !configuredTtl.equals(localTtl) || configuredMaximumSize != localMaximumSize) {
            synchronized (this) {
                if (localCache == null || !configuredTtl.equals(localTtl) || configuredMaximumSize != localMaximumSize) {
                    localCache = Caffeine.newBuilder().expireAfterWrite(configuredTtl)
                            .maximumSize(configuredMaximumSize).build();
                    localTtl = configuredTtl;
                    localMaximumSize = configuredMaximumSize;
                }
                current = localCache;
            }
        }
        return current;
    }
}
