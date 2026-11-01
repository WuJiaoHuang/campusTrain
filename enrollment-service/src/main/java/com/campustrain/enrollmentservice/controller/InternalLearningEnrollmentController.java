package com.campustrain.enrollmentservice.controller;

import com.campustrain.enrollmentservice.common.Result;
import com.campustrain.enrollmentservice.service.LearningEnrollmentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/learning")
public class InternalLearningEnrollmentController {
    private final LearningEnrollmentService learningEnrollmentService;

    public InternalLearningEnrollmentController(LearningEnrollmentService learningEnrollmentService) {
        this.learningEnrollmentService = learningEnrollmentService;
    }

@GetMapping("/check-enrollment/{trainingId}/user/{userId}")
    public Result<Boolean> isEnrollmentActive(@PathVariable("trainingId") Long trainingId,
                                  @PathVariable("userId") Long userId) {
        return Result.success(learningEnrollmentService.isEnrollmentActive(trainingId, userId));
    }
}
