package com.campustrain.enrollmentservice.service;

public interface LearningEnrollmentService {
    boolean isEnrollmentActive(Long trainingId, Long userId);
}
