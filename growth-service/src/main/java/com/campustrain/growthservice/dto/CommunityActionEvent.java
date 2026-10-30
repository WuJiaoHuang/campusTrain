package com.campustrain.growthservice.dto;

public record CommunityActionEvent(String eventId, String eventType, Long userId, Long targetId,
                                   String targetType, String actionType, String occurredAt, String traceId) { }
