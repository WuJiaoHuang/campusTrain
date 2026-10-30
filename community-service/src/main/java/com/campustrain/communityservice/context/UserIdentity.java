package com.campustrain.communityservice.context;

public record UserIdentity(Long userId, String role, String username, String traceId) { }
