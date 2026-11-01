package com.campustrain.communityservice.config;

public final class LikeKeys {
    private static final String PREFIX = "campustrain:community:like:";

    private LikeKeys() {
    }

    public static String users(String targetType, long targetId) {
        return PREFIX + "users:" + targetType + ":" + targetId;
    }

    public static String count(String targetType, long targetId) {
        return PREFIX + "count:" + targetType + ":" + targetId;
    }

    public static String revision(String targetType, long targetId) {
        return PREFIX + "revision:" + targetType + ":" + targetId;
    }

    public static String initialized(String targetType, long targetId) {
        return PREFIX + "init:" + targetType + ":" + targetId;
    }

    public static String lock(String targetType, long targetId) {
        return PREFIX + "lock:" + targetType + ":" + targetId;
    }

    public static String flushLock(String targetType, long targetId) {
        return PREFIX + "flush-lock:" + targetType + ":" + targetId;
    }

    public static String dirty() {
        return PREFIX + "dirty";
    }

    public static String member(String targetType, long targetId) {
        return targetType + ":" + targetId;
    }
}
