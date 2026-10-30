package com.campustrain.communityservice.entity;

import java.time.LocalDateTime;

public record Comment(Long id, String targetType, Long targetId, Long userId,
                      String content, LocalDateTime createTime) { }
