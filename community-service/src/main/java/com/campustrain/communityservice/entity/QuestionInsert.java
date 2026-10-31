package com.campustrain.communityservice.entity;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class QuestionInsert {
    private Long id;
    private Long trainingId;
    private Long courseId;
    private Long lessonId;
    private Long userId;
    private String title;
    private String content;
}
