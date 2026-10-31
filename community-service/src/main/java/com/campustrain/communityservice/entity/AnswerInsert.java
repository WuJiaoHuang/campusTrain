package com.campustrain.communityservice.entity;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AnswerInsert {
    private Long id;
    private Long questionId;
    private Long userId;
    private String content;
}
