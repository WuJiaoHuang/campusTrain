package com.campustrain.communityservice.vo;

import java.time.LocalDateTime;
import java.util.List;

public record AnswerView(Long id, Long questionId, Long userId, String content, int commentCount,
                         long likeCount, boolean likedByMe, LocalDateTime createTime,
                         List<CommentView> comments) { }
