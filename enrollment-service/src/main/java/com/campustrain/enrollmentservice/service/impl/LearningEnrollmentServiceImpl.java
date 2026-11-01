package com.campustrain.enrollmentservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campustrain.enrollmentservice.entity.Enrollment;
import com.campustrain.enrollmentservice.entity.EnrollmentStatus;
import com.campustrain.enrollmentservice.mapper.EnrollmentMapper;
import com.campustrain.enrollmentservice.service.LearningEnrollmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LearningEnrollmentServiceImpl implements LearningEnrollmentService {
    private final EnrollmentMapper enrollmentMapper;

    @Override
    public boolean isEnrollmentActive(Long trainingId, Long userId) {
        return enrollmentMapper.exists(new LambdaQueryWrapper<Enrollment>()
                .eq(Enrollment::getTrainingId, trainingId)
                .eq(Enrollment::getUserId, userId)
                .eq(Enrollment::getStatus, EnrollmentStatus.ENROLLED.getCode()));
    }
}
