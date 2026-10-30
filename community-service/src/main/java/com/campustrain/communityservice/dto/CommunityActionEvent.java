package com.campustrain.communityservice.dto;

public record CommunityActionEvent(String eventId, String eventType, Long userId, Long targetId,
                                   String targetType, String actionType, String occurredAt, String traceId) { }
