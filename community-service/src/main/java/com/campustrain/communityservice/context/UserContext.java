package com.campustrain.communityservice.context;

public final class UserContext {
    private static final ThreadLocal<UserIdentity> CURRENT = new ThreadLocal<>();
    private UserContext() { }
    public static UserIdentity current() { return CURRENT.get(); }
    static void set(UserIdentity identity) { CURRENT.set(identity); }
    static void clear() { CURRENT.remove(); }
    public static String traceId() { return current() == null ? null : current().traceId(); }
}
