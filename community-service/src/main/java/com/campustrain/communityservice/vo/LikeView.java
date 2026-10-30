package com.campustrain.communityservice.vo;

public record LikeView(String targetType, Long targetId, long likeCount, boolean likedByMe, boolean changed) { }
