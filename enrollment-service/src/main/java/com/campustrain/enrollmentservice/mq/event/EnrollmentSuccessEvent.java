package com.campustrain.enrollmentservice.mq.event;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class EnrollmentSuccessEvent {
    private String eventId;
    private String eventType = "ENROLLMENT_SUCCESS";
    private Long userId;
    private Long trainingId;
    private Long enrollmentId;
    private LocalDateTime occurredAt;
    private String traceId;
}
