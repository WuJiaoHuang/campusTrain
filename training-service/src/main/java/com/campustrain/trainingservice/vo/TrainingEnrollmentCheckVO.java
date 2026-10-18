package com.campustrain.trainingservice.vo;

import java.time.LocalDateTime;

public record TrainingEnrollmentCheckVO(Long trainingId, Integer status, Integer capacity,
        Integer enrolledCount, LocalDateTime enrollStartTime, LocalDateTime enrollEndTime) {
}
