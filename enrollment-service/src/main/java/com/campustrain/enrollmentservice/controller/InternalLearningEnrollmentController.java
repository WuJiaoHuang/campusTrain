package com.campustrain.enrollmentservice.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campustrain.enrollmentservice.common.Result;
import com.campustrain.enrollmentservice.entity.Enrollment;
import com.campustrain.enrollmentservice.entity.EnrollmentStatus;
import com.campustrain.enrollmentservice.mapper.EnrollmentMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/learning/enrollments")
public class InternalLearningEnrollmentController {
    private final EnrollmentMapper mapper;

    public InternalLearningEnrollmentController(EnrollmentMapper mapper) {
        this.mapper = mapper;
    }

    @GetMapping("/{trainingId}/users/{userId}")
    public Result<Boolean> active(@PathVariable("trainingId") Long trainingId,
                                  @PathVariable("userId") Long userId) {
        return Result.success(mapper.exists(new LambdaQueryWrapper<Enrollment>()
                .eq(Enrollment::getTrainingId, trainingId)
                .eq(Enrollment::getUserId, userId)
                .eq(Enrollment::getStatus, EnrollmentStatus.ENROLLED.getCode())));
    }
}
