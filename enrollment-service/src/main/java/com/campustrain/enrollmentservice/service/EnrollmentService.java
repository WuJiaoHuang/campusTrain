package com.campustrain.enrollmentservice.service;

import com.campustrain.enrollmentservice.dto.EnrollmentCreateDTO;

public interface EnrollmentService {

    Long enroll(EnrollmentCreateDTO dto);
}
