package com.campustrain.learningservice.context;

public record UserIdentity(Long userId, String role, String username, String traceId) {
}
