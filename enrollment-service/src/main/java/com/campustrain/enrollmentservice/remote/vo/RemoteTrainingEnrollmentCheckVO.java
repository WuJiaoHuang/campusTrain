package com.campustrain.enrollmentservice.remote.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class RemoteTrainingEnrollmentCheckVO {
    private Long trainingId;
    private Long teacherId;
    private Integer status;
    private Integer capacity;
    private Integer enrolledCount;
    private LocalDateTime enrollStartTime;
    private LocalDateTime enrollEndTime;
}
