package com.campustrain.enrollmentservice.concurrency;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campustrain.enrollmentservice.context.UserContext;
import com.campustrain.enrollmentservice.entity.Enrollment;
import com.campustrain.enrollmentservice.entity.EnrollmentStatus;
import com.campustrain.enrollmentservice.exception.BusinessException;
import com.campustrain.enrollmentservice.mapper.EnrollmentMapper;
import com.campustrain.enrollmentservice.remote.vo.RemoteTrainingEnrollmentCheckVO;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RScript;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EnrollmentRedisReservationService {

    private static final long INITIALIZATION_LOCK_WAIT_SECONDS = 3;

    private final RedissonClient redissonClient;
    private final EnrollmentMapper enrollmentMapper;

    private String reserveScript;
    private String releaseScript;

    public EnrollmentRedisReservationService(RedissonClient redissonClient, EnrollmentMapper enrollmentMapper) {
        this.redissonClient = redissonClient;
        this.enrollmentMapper = enrollmentMapper;
    }

    @PostConstruct
    void loadScripts() {
        reserveScript = readScript("lua/enrollment-reserve.lua");
        releaseScript = readScript("lua/enrollment-release.lua");
    }

    public void ensureInitialized(RemoteTrainingEnrollmentCheckVO training) {
        Long trainingId = training.getTrainingId();
        try {
            if (redissonClient.getBucket(EnrollmentRedisKeys.initialized(trainingId), StringCodec.INSTANCE).isExists()) {
                return;
            }
            initializeUnderLock(training);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw unavailable(trainingId, "initialize", e);
        }
    }

    public ReservationResult reserve(Long trainingId, Long userId) {
        return executeScript(reserveScript, trainingId, userId, "reserve");
    }

    /**
     * Used both after a failed enrollment and after a confirmed cancellation. The Lua
     * script increments stock only when it actually removes a current active user.
     */
    public ReservationResult release(Long trainingId, Long userId) {
        return executeScript(releaseScript, trainingId, userId, "release");
    }

    private void initializeUnderLock(RemoteTrainingEnrollmentCheckVO training) throws InterruptedException {
        Long trainingId = training.getTrainingId();
        RLock lock = redissonClient.getLock(EnrollmentRedisKeys.initializationLock(trainingId));
        boolean locked = false;
        try {
            // No lease time keeps Redisson's WatchDog active while initialization is running.
            locked = lock.tryLock(INITIALIZATION_LOCK_WAIT_SECONDS, TimeUnit.SECONDS);
            if (!locked) {
                log.error("enrollment Redis initialization lock was not acquired, traceId={}, trainingId={}",
                        UserContext.getTraceId(), trainingId);
                throw new BusinessException(503, "报名高并发控制暂时不可用，请稍后重试");
            }
            if (redissonClient.getBucket(EnrollmentRedisKeys.initialized(trainingId), StringCodec.INSTANCE).isExists()) {
                return;
            }

            List<String> activeUserIds = enrollmentMapper.selectList(new LambdaQueryWrapper<Enrollment>()
                            .eq(Enrollment::getTrainingId, trainingId)
                            .eq(Enrollment::getStatus, EnrollmentStatus.ENROLLED.getCode()))
                    .stream()
                    .map(enrollment -> String.valueOf(enrollment.getUserId()))
                    .toList();
            int capacity = training.getCapacity();
            int stock = capacity - activeUserIds.size();
            if (stock < 0) {
                log.error("enrollment Redis initialization rejected inconsistent DB count, traceId={}, trainingId={}, capacity={}, activeCount={}",
                        UserContext.getTraceId(), trainingId, capacity, activeUserIds.size());
                throw new BusinessException(503, "报名数据不一致，请稍后重试");
            }
            if (training.getEnrolledCount() != null && training.getEnrolledCount() != activeUserIds.size()) {
                log.error("enrollment Redis initialization detected count mismatch, traceId={}, trainingId={}, trainingCount={}, activeCount={}",
                        UserContext.getTraceId(), trainingId, training.getEnrolledCount(), activeUserIds.size());
                throw new BusinessException(503, "报名数据不一致，请稍后重试");
            }

            RSet<String> users = redissonClient.getSet(EnrollmentRedisKeys.users(trainingId), StringCodec.INSTANCE);
            users.delete();
            if (!activeUserIds.isEmpty()) {
                users.addAll(activeUserIds);
            }
            RBucket<String> stockBucket = redissonClient.getBucket(EnrollmentRedisKeys.stock(trainingId), StringCodec.INSTANCE);
            stockBucket.set(String.valueOf(stock));
            redissonClient.getBucket(EnrollmentRedisKeys.initialized(trainingId), StringCodec.INSTANCE).set("1");
            log.info("enrollment Redis state initialized, traceId={}, trainingId={}, stock={}, activeCount={}",
                    UserContext.getTraceId(), trainingId, stock, activeUserIds.size());
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private ReservationResult executeScript(String script, Long trainingId, Long userId, String action) {
        try {
            Number result = redissonClient.getScript().eval(RScript.Mode.READ_WRITE, script, RScript.ReturnType.LONG,
                    List.of(EnrollmentRedisKeys.stock(trainingId), EnrollmentRedisKeys.users(trainingId), EnrollmentRedisKeys.initialized(trainingId)),
                    String.valueOf(userId));
            return ReservationResult.fromCode(result);
        } catch (Exception e) {
            throw unavailable(trainingId, action, e);
        }
    }

    private BusinessException unavailable(Long trainingId, String action, Exception e) {
        log.error("enrollment Redis operation failed, traceId={}, userId={}, trainingId={}, action={}",
                UserContext.getTraceId(), UserContext.getUserId(), trainingId, action, e);
        return new BusinessException(503, "报名高并发控制暂时不可用，请稍后重试");
    }

    private String readScript(String location) {
        try (var input = new ClassPathResource(location).getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("cannot load Redis Lua script " + location, e);
        }
    }
}
