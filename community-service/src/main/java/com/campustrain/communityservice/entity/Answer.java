package com.campustrain.communityservice.entity;

import java.time.LocalDateTime;

public record Answer(Long id, Long questionId, Long userId, String content,
                     int commentCount, long likeCount, LocalDateTime createTime) { }
