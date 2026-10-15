package com.campustrain.enrollmentservice.context;

public final class UserContext {

    private static final ThreadLocal<UserContextData> CONTEXT = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(Long userId, String role, String username, String traceId) {
        CONTEXT.set(new UserContextData(userId, role, username, traceId));
    }

    public static Long getUserId() {
        UserContextData context = CONTEXT.get();
        return context == null ? null : context.userId();
    }

    public static String getRole() {
        UserContextData context = CONTEXT.get();
        return context == null ? null : context.role();
    }

    public static String getUsername() {
        UserContextData context = CONTEXT.get();
        return context == null ? null : context.username();
    }

    public static String getTraceId() {
        UserContextData context = CONTEXT.get();
        return context == null ? null : context.traceId();
    }

    public static void clear() {
        CONTEXT.remove();
    }

    private record UserContextData(Long userId, String role, String username, String traceId) {
    }
}
