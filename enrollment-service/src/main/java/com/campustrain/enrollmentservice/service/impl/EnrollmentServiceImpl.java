package com.campustrain.enrollmentservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campustrain.enrollmentservice.client.TrainingClient;
import com.campustrain.enrollmentservice.client.UserClient;
import com.campustrain.enrollmentservice.dto.EnrollmentCreateDTO;
import com.campustrain.enrollmentservice.entity.Enrollment;
import com.campustrain.enrollmentservice.exception.BusinessException;
import com.campustrain.enrollmentservice.mapper.EnrollmentMapper;
import com.campustrain.enrollmentservice.remote.RemoteResult;
import com.campustrain.enrollmentservice.remote.vo.RemoteTrainingVO;
import com.campustrain.enrollmentservice.remote.vo.RemoteUserVO;
import com.campustrain.enrollmentservice.service.EnrollmentService;
import feign.FeignException;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class EnrollmentServiceImpl extends ServiceImpl<EnrollmentMapper, Enrollment> implements EnrollmentService {

    private static final int SUCCESS_CODE = 200;
    private static final int ENABLED_STATUS = 1;
    private static final String STUDENT_ROLE = "STUDENT";

    private final UserClient userClient;
    private final TrainingClient trainingClient;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long enroll(EnrollmentCreateDTO dto) {
        if (hasActiveEnrollment(dto.getUserId(), dto.getTrainingId())) {
            log.warn("duplicate enrollment requested, userId={}, trainingId={}", dto.getUserId(), dto.getTrainingId());
            throw new BusinessException("请勿重复报名");
        }

        RemoteUserVO user = getRemoteUser(dto.getUserId());
        validateUser(user);

        RemoteTrainingVO training = getRemoteTraining(dto.getTrainingId());
        validateTraining(training);

        LocalDateTime now = LocalDateTime.now();
        Enrollment enrollment = new Enrollment();
        enrollment.setUserId(dto.getUserId());
        enrollment.setTrainingId(dto.getTrainingId());
        enrollment.setStatus(ENABLED_STATUS);
        enrollment.setEnrollTime(now);
        enrollment.setCreateTime(now);
        enrollment.setUpdateTime(now);
        try {
            save(enrollment);
        } catch (DuplicateKeyException e) {
            log.warn("duplicate enrollment blocked by database, userId={}, trainingId={}", dto.getUserId(), dto.getTrainingId(), e);
            throw new BusinessException("请勿重复报名");
        }

        log.info(
                "enrollment created, enrollmentId={}, userId={}, trainingId={}",
                enrollment.getId(),
                enrollment.getUserId(),
                enrollment.getTrainingId()
        );
        return enrollment.getId();
    }

    private boolean hasActiveEnrollment(Long userId, Long trainingId) {
        return exists(new LambdaQueryWrapper<Enrollment>()
                .eq(Enrollment::getUserId, userId)
                .eq(Enrollment::getTrainingId, trainingId)
                .eq(Enrollment::getStatus, ENABLED_STATUS));
    }

    private RemoteUserVO getRemoteUser(Long userId) {
        RemoteResult<RemoteUserVO> result;
        try {
            result = userClient.getUserById(userId);
        } catch (FeignException e) {
            log.error("remote user query failed, userId={}, httpStatus={}", userId, e.status(), e);
            throw new BusinessException(503, "用户服务暂时不可用");
        } catch (Exception e) {
            log.error("remote user query failed, userId={}, httpStatus=unavailable", userId, e);
            throw new BusinessException(503, "用户服务暂时不可用");
        }

        if (result == null || result.getCode() == null || result.getCode() != SUCCESS_CODE || result.getData() == null) {
            log.warn("remote user query returned failure, userId={}, code={}, message={}",
                    userId,
                    result == null ? null : result.getCode(),
                    result == null ? null : result.getMessage());
            throw new BusinessException("用户不存在");
        }
        return result.getData();
    }

    private void validateUser(RemoteUserVO user) {
        if (!Integer.valueOf(ENABLED_STATUS).equals(user.getStatus())) {
            throw new BusinessException("用户已被禁用，无法报名");
        }
        if (!STUDENT_ROLE.equals(user.getRole())) {
            throw new BusinessException("仅学生用户可以报名");
        }
    }

    private RemoteTrainingVO getRemoteTraining(Long trainingId) {
        RemoteResult<RemoteTrainingVO> result;
        try {
            result = trainingClient.getTrainingById(trainingId);
        } catch (FeignException e) {
            log.error("remote training query failed, trainingId={}, httpStatus={}", trainingId, e.status(), e);
            throw new BusinessException(503, "培训服务暂时不可用");
        } catch (Exception e) {
            log.error("remote training query failed, trainingId={}, httpStatus=unavailable", trainingId, e);
            throw new BusinessException(503, "培训服务暂时不可用");
        }

        if (result == null || result.getCode() == null || result.getCode() != SUCCESS_CODE || result.getData() == null) {
            log.warn("remote training query returned failure, trainingId={}, code={}, message={}",
                    trainingId,
                    result == null ? null : result.getCode(),
                    result == null ? null : result.getMessage());
            throw new BusinessException("培训不存在");
        }
        return result.getData();
    }

    private void validateTraining(RemoteTrainingVO training) {
        if (!Integer.valueOf(ENABLED_STATUS).equals(training.getStatus())) {
            throw new BusinessException("当前培训不在报名状态");
        }

        LocalDateTime now = LocalDateTime.now();
        if (training.getEnrollStartTime() != null && now.isBefore(training.getEnrollStartTime())) {
            throw new BusinessException("报名尚未开始");
        }
        if (training.getEnrollEndTime() != null && now.isAfter(training.getEnrollEndTime())) {
            throw new BusinessException("报名已结束");
        }
        if (training.getCapacity() == null || training.getEnrolledCount() == null
                || training.getEnrolledCount() >= training.getCapacity()) {
            throw new BusinessException("培训名额已满");
        }
    }
}
