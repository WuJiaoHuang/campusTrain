package com.campustrain.communityservice.entity;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CommentInsert {
    private Long id;
    private String targetType;
    private Long targetId;
    private Long userId;
    private String content;
}
