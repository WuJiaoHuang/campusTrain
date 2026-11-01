package com.campustrain.enrollmentservice.service;

import com.campustrain.enrollmentservice.mq.event.EnrollmentSuccessEvent;

public interface NotificationService {
    void processEnrollmentSuccess(EnrollmentSuccessEvent event) throws Exception;
}
