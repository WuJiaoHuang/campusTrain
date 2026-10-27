package com.campustrain.learningservice.client.dto;

public record Lesson(Long lessonId, Integer lessonType, Integer videoDuration, Long chapterId,
                     Long courseId, Long trainingId, boolean videoBound) {
}
