package com.campustrain.trainingservice.vo;

public record LessonInfoVO(Long lessonId, Integer lessonType, Integer videoDuration,
                           Long chapterId, Long courseId, Long trainingId, boolean videoBound) {}
