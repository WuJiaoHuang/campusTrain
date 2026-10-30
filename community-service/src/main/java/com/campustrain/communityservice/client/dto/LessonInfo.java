package com.campustrain.communityservice.client.dto;

public record LessonInfo(Long lessonId, Integer lessonType, Integer videoDuration,
                         Long chapterId, Long courseId, Long trainingId, boolean videoBound) { }
