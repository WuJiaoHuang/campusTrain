package com.campustrain.communityservice.entity;

import java.time.LocalDateTime;

public record Question(Long id, Long trainingId, Long courseId, Long lessonId, Long userId,
                       String title, String content, int answerCount, long likeCount, LocalDateTime createTime) { }
