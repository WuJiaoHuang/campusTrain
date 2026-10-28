package com.campustrain.growthservice;

/** Stage15/16 event contract consumed by the independent growth queue. */
public record LearningCompletedEvent(String eventId, String eventType, Long userId, Long trainingId,
                                    Long courseId, Long lessonId, String occurredAt, String traceId) { }
