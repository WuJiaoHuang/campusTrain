package com.campustrain.learningservice.vo;

public record LearningProgressVO(long lessonId, long progressSeconds, long durationSeconds,
                                 boolean completed, String lastLearnTime) {
}
