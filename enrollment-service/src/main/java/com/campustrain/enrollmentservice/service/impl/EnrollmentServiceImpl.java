package com.campustrain.enrollmentservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campustrain.enrollmentservice.client.TrainingClient;
import com.campustrain.enrollmentservice.client.UserClient;
import com.campustrain.enrollmentservice.context.UserContext;
import com.campustrain.enrollmentservice.dto.EnrollmentCreateDTO;
import com.campustrain.enrollmentservice.entity.Enrollment;
import com.campustrain.enrollmentservice.entity.EnrollmentStatus;
import com.campustrain.enrollmentservice.vo.EnrollmentVO;
import com.campustrain.enrollmentservice.exception.BusinessException;
import com.campustrain.enrollmentservice.mapper.EnrollmentMapper;
import com.campustrain.enrollmentservice.remote.RemoteResult;
import com.campustrain.enrollmentservice.remote.vo.RemoteTrainingEnrollmentCheckVO;
import com.campustrain.enrollmentservice.remote.vo.RemoteUserVO;
import com.campustrain.enrollmentservice.service.EnrollmentService;
import feign.FeignException;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class EnrollmentServiceImpl extends ServiceImpl<EnrollmentMapper, Enrollment> implements EnrollmentService {

    private static final int SUCCESS_CODE = 200;
    private static final int ENABLED_STATUS = 1;
    private static final String STUDENT_ROLE = "STUDENT";

    private final UserClient userClient;
    private final TrainingClient trainingClient;
    private final PlatformTransactionManager transactionManager;

    @Override
    public Long enroll(EnrollmentCreateDTO dto) {
        Long userId = getCurrentUserId();
        validateGatewayRole();
        String traceId = UserContext.getTraceId();
        if (hasActiveEnrollment(userId, dto.getTrainingId())) {
            log.warn("duplicate enrollment requested, traceId={}, userId={}, trainingId={}", traceId, userId, dto.getTrainingId());
            throw new BusinessException("请勿重复报名");
        }

        RemoteUserVO user = getRemoteUser(userId);
        validateUser(user);

        RemoteTrainingEnrollmentCheckVO training = getRemoteTraining(dto.getTrainingId());
        validateTraining(training);

        callCount(() -> trainingClient.increment(dto.getTrainingId()), dto.getTrainingId(), true);
        try {
            Long id = new TransactionTemplate(transactionManager).execute(tx -> {
                Enrollment existing = getOne(new LambdaQueryWrapper<Enrollment>()
                        .eq(Enrollment::getUserId, userId).eq(Enrollment::getTrainingId, dto.getTrainingId())
                        .last("FOR UPDATE"));
                LocalDateTime now = LocalDateTime.now();
                if (existing != null) {
                    if (!Integer.valueOf(EnrollmentStatus.CANCELLED.getCode()).equals(existing.getStatus())) {
                        throw new BusinessException("请勿重复报名");
                    }
                    boolean updated = update(new LambdaUpdateWrapper<Enrollment>()
                            .eq(Enrollment::getId, existing.getId())
                            .eq(Enrollment::getStatus, EnrollmentStatus.CANCELLED.getCode())
                            .set(Enrollment::getStatus, EnrollmentStatus.ENROLLED.getCode())
                            .set(Enrollment::getEnrollTime, now).set(Enrollment::getUpdateTime, now));
                    if (!updated) throw new BusinessException("报名状态已变化，请重试");
                    return existing.getId();
                }
                Enrollment enrollment = new Enrollment();
                enrollment.setUserId(userId);
                enrollment.setTrainingId(dto.getTrainingId());
                enrollment.setStatus(EnrollmentStatus.ENROLLED.getCode());
                enrollment.setEnrollTime(now);
                enrollment.setCreateTime(now);
                enrollment.setUpdateTime(now);
                if (!save(enrollment)) throw new IllegalStateException("报名记录写入失败");
                return enrollment.getId();
            });
            log.info("enrollment created or reactivated, traceId={}, enrollmentId={}, userId={}, trainingId={}",
                    traceId, id, userId, dto.getTrainingId());
            return id;
        } catch (RuntimeException e) {
            compensate(() -> trainingClient.decrement(dto.getTrainingId()), userId, dto.getTrainingId(), "release", e);
            if (e instanceof DuplicateKeyException) throw new BusinessException("请勿重复报名");
            throw e;
        }
    }

    @Override
    public void cancel(Long enrollmentId) {
        Long userId = getCurrentUserId();
        AtomicBoolean released = new AtomicBoolean();
        Long[] trainingId = new Long[1];
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
                Enrollment enrollment = getOne(new LambdaQueryWrapper<Enrollment>()
                        .eq(Enrollment::getId, enrollmentId).eq(Enrollment::getUserId, userId).last("FOR UPDATE"));
                if (enrollment == null) throw new BusinessException("报名不存在或不属于当前用户");
                if (!Integer.valueOf(EnrollmentStatus.ENROLLED.getCode()).equals(enrollment.getStatus())) {
                    log.warn("cancellation rejected, traceId={}, userId={}, trainingId={}, enrollmentId={}, status={}",
                            UserContext.getTraceId(), userId, enrollment.getTrainingId(), enrollmentId, enrollment.getStatus());
                    throw new BusinessException("报名已取消");
                }
                trainingId[0] = enrollment.getTrainingId();
                boolean updated = update(new LambdaUpdateWrapper<Enrollment>()
                        .eq(Enrollment::getId, enrollmentId).eq(Enrollment::getUserId, userId)
                        .eq(Enrollment::getStatus, EnrollmentStatus.ENROLLED.getCode())
                        .set(Enrollment::getStatus, EnrollmentStatus.CANCELLED.getCode())
                        .set(Enrollment::getUpdateTime, LocalDateTime.now()));
                if (!updated) throw new BusinessException("报名状态已变化，请重试");
                callCount(() -> trainingClient.decrement(trainingId[0]), trainingId[0], false);
                released.set(true);
            });
            log.info("enrollment cancelled, traceId={}, userId={}, trainingId={}, enrollmentId={}",
                    UserContext.getTraceId(), userId, trainingId[0], enrollmentId);
        } catch (RuntimeException e) {
            if (released.get()) compensate(() -> trainingClient.restore(trainingId[0]), userId, trainingId[0], "restore", e);
            throw e;
        }
    }

    @Override
    public Page<EnrollmentVO> myEnrollments(long page, long size, Integer status) {
        return queryPage(page, size, status, getCurrentUserId(), null);
    }

    @Override
    public Page<EnrollmentVO> trainingEnrollments(Long trainingId, long page, long size, Integer status) {
        Long userId = getCurrentUserId();
        String role = UserContext.getRole();
        if (!"ADMIN".equals(role) && !"TEACHER".equals(role)) {
            log.warn("roster role rejected, traceId={}, userId={}, trainingId={}, role={}", UserContext.getTraceId(), userId, trainingId, role);
            throw new BusinessException("无权查看报名名单");
        }
        RemoteUserVO user = getRemoteUser(userId);
        if (!Integer.valueOf(ENABLED_STATUS).equals(user.getStatus()) || !role.equals(user.getRole())) {
            throw new BusinessException("用户状态或角色无效");
        }
        RemoteTrainingEnrollmentCheckVO training = getRemoteTraining(trainingId);
        if ("TEACHER".equals(role) && !userId.equals(training.getTeacherId())) throw new BusinessException("只能查看自己负责的培训");
        return queryPage(page, size, status, null, trainingId);
    }

    private Page<EnrollmentVO> queryPage(long pageNumber, long size, Integer status, Long userId, Long trainingId) {
        if (status != null && status != EnrollmentStatus.ENROLLED.getCode() && status != EnrollmentStatus.CANCELLED.getCode()) {
            throw new BusinessException("报名状态无效");
        }
        Page<Enrollment> result = page(new Page<>(pageNumber, size), new LambdaQueryWrapper<Enrollment>()
                .eq(userId != null, Enrollment::getUserId, userId)
                .eq(trainingId != null, Enrollment::getTrainingId, trainingId)
                .eq(status != null, Enrollment::getStatus, status)
                .orderByDesc(Enrollment::getId));
        Page<EnrollmentVO> output = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        output.setRecords(result.getRecords().stream().map(item -> {
            EnrollmentVO vo = new EnrollmentVO();
            vo.setEnrollmentId(item.getId()); vo.setUserId(item.getUserId()); vo.setTrainingId(item.getTrainingId());
            vo.setStatus(item.getStatus()); vo.setEnrollTime(item.getEnrollTime()); vo.setCreateTime(item.getCreateTime());
            return vo;
        }).toList());
        return output;
    }

    private void callCount(Supplier<RemoteResult<Void>> call, Long trainingId, boolean reservation) {
        RemoteResult<Void> result;
        try {
            result = call.get();
        } catch (Exception e) {
            log.error("training count call failed, traceId={}, userId={}, trainingId={}",
                    UserContext.getTraceId(), UserContext.getUserId(), trainingId, e);
            throw new BusinessException(503, "培训服务暂时不可用");
        }
        if (result == null || result.getCode() == null || result.getCode() == 503 || result.getCode() >= 500) {
            log.error("training count unavailable, traceId={}, userId={}, trainingId={}", UserContext.getTraceId(), UserContext.getUserId(), trainingId);
            throw new BusinessException(503, "培训服务暂时不可用");
        }
        if (result.getCode() != SUCCESS_CODE) {
            if (reservation) throw new BusinessException("培训不可报名或名额已满");
            throw new BusinessException(503, "培训名额计数异常");
        }
    }

    private void compensate(Supplier<RemoteResult<Void>> operation, Long userId, Long trainingId, String action, Exception original) {
        try {
            RemoteResult<Void> result = operation.get();
            if (result == null || !Integer.valueOf(SUCCESS_CODE).equals(result.getCode())) {
                throw new IllegalStateException("compensation returned failure");
            }
        } catch (Exception failure) {
            log.error("enrollment compensation failed, traceId={}, userId={}, trainingId={}, action={}",
                    UserContext.getTraceId(), userId, trainingId, action, failure);
            original.addSuppressed(failure);
        }
    }

    private Long getCurrentUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(401, "用户身份缺失，请先登录");
        }
        return userId;
    }

    private void validateGatewayRole() {
        String role = UserContext.getRole();
        if (StringUtils.hasText(role) && !STUDENT_ROLE.equals(role)) {
            log.warn("enrollment role rejected, traceId={}, userId={}, role={}", UserContext.getTraceId(), UserContext.getUserId(), role);
            throw new BusinessException("仅学生用户可以报名");
        }
    }

    private boolean hasActiveEnrollment(Long userId, Long trainingId) {
        return exists(new LambdaQueryWrapper<Enrollment>()
                .eq(Enrollment::getUserId, userId)
                .eq(Enrollment::getTrainingId, trainingId)
                .eq(Enrollment::getStatus, EnrollmentStatus.ENROLLED.getCode()));
    }

    private RemoteUserVO getRemoteUser(Long userId) {
        RemoteResult<RemoteUserVO> result;
        try {
            result = userClient.getUserById(userId);
        } catch (FeignException e) {
            log.error("remote user query failed, traceId={}, userId={}, httpStatus={}",
                    UserContext.getTraceId(), userId, e.status(), e);
            throw new BusinessException(503, "用户服务暂时不可用");
        } catch (Exception e) {
            log.error("remote user query failed, traceId={}, userId={}, httpStatus=unavailable",
                    UserContext.getTraceId(), userId, e);
            throw new BusinessException(503, "用户服务暂时不可用");
        }

        if (result != null && result.getCode() != null && result.getCode() >= 500) {
            log.error("remote user unavailable, traceId={}, userId={}, code={}", UserContext.getTraceId(), userId, result.getCode());
            throw new BusinessException(503, "用户服务暂时不可用");
        }
        if (result == null || result.getCode() == null || result.getCode() != SUCCESS_CODE || result.getData() == null) {
            log.warn("remote user query returned failure, traceId={}, userId={}, code={}, message={}",
                    UserContext.getTraceId(),
                    userId,
                    result == null ? null : result.getCode(),
                    result == null ? null : result.getMessage());
            throw new BusinessException("用户不存在");
        }
        return result.getData();
    }

    private void validateUser(RemoteUserVO user) {
        if (!Integer.valueOf(ENABLED_STATUS).equals(user.getStatus())) {
            log.warn("enrollment user disabled, traceId={}, userId={}", UserContext.getTraceId(), UserContext.getUserId());
            throw new BusinessException("用户已被禁用，无法报名");
        }
        if (!STUDENT_ROLE.equals(user.getRole())) {
            log.warn("enrollment user role rejected, traceId={}, userId={}, role={}", UserContext.getTraceId(), UserContext.getUserId(), user.getRole());
            throw new BusinessException("仅学生用户可以报名");
        }
    }

    private RemoteTrainingEnrollmentCheckVO getRemoteTraining(Long trainingId) {
        RemoteResult<RemoteTrainingEnrollmentCheckVO> result;
        try {
            result = trainingClient.getTrainingById(trainingId);
        } catch (FeignException e) {
            log.error("remote training query failed, traceId={}, trainingId={}, httpStatus={}",
                    UserContext.getTraceId(), trainingId, e.status(), e);
            throw new BusinessException(503, "培训服务暂时不可用");
        } catch (Exception e) {
            log.error("remote training query failed, traceId={}, trainingId={}, httpStatus=unavailable",
                    UserContext.getTraceId(), trainingId, e);
            throw new BusinessException(503, "培训服务暂时不可用");
        }

        if (result != null && result.getCode() != null && result.getCode() >= 500) {
            log.error("remote training unavailable, traceId={}, userId={}, trainingId={}, code={}", UserContext.getTraceId(), UserContext.getUserId(), trainingId, result.getCode());
            throw new BusinessException(503, "培训服务暂时不可用");
        }
        if (result == null || result.getCode() == null || result.getCode() != SUCCESS_CODE || result.getData() == null) {
            log.warn("remote training query returned failure, traceId={}, trainingId={}, code={}, message={}",
                    UserContext.getTraceId(),
                    trainingId,
                    result == null ? null : result.getCode(),
                    result == null ? null : result.getMessage());
            throw new BusinessException("培训不存在");
        }
        return result.getData();
    }

    private void validateTraining(RemoteTrainingEnrollmentCheckVO training) {
        if (!Integer.valueOf(ENABLED_STATUS).equals(training.getStatus())) {
            log.warn("enrollment training status rejected, traceId={}, userId={}, trainingId={}, status={}", UserContext.getTraceId(), UserContext.getUserId(), training.getTrainingId(), training.getStatus());
            throw new BusinessException("当前培训不在报名状态");
        }

        LocalDateTime now = LocalDateTime.now();
        if (training.getEnrollStartTime() != null && now.isBefore(training.getEnrollStartTime())) {
            log.warn("enrollment window not started, traceId={}, userId={}, trainingId={}", UserContext.getTraceId(), UserContext.getUserId(), training.getTrainingId());
            throw new BusinessException("报名尚未开始");
        }
        if (training.getEnrollEndTime() != null && now.isAfter(training.getEnrollEndTime())) {
            log.warn("enrollment window ended, traceId={}, userId={}, trainingId={}", UserContext.getTraceId(), UserContext.getUserId(), training.getTrainingId());
            throw new BusinessException("报名已结束");
        }
        if (training.getCapacity() == null || training.getEnrolledCount() == null
                || training.getEnrolledCount() >= training.getCapacity()) {
            log.warn("enrollment capacity rejected, traceId={}, userId={}, trainingId={}", UserContext.getTraceId(), UserContext.getUserId(), training.getTrainingId());
            throw new BusinessException("培训名额已满");
        }
    }
}
