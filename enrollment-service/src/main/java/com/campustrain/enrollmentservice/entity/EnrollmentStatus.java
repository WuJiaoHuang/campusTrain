package com.campustrain.enrollmentservice.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum EnrollmentStatus {
    CANCELLED(0), ENROLLED(1);
    private final int code;
}
