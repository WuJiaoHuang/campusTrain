package com.campustrain.communityservice.vo;

import java.time.LocalDateTime;

public record CommentView(Long id, String targetType, Long targetId, Long userId, String content,
                          LocalDateTime createTime) { }
