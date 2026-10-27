package com.campustrain.learningservice.context;

public final class RequestIdentityContext {
    private static final ThreadLocal<UserIdentity> CURRENT = new ThreadLocal<>();

    private RequestIdentityContext() {
    }

    public static UserIdentity current() {
        return CURRENT.get();
    }

    static void set(UserIdentity identity) {
        CURRENT.set(identity);
    }

    static void clear() {
        CURRENT.remove();
    }
}
