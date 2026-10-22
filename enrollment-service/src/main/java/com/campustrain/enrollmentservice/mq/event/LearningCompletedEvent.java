package com.campustrain.enrollmentservice.mq.event;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class LearningCompletedEvent {
    private String eventId;
    private String eventType = "LEARNING_COMPLETED";
    private Long userId;
    private Long trainingId;
    private Long courseId;
    private Long lessonId;
    private LocalDateTime occurredAt;
    private String traceId;
}
