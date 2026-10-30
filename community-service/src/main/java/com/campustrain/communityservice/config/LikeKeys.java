package com.campustrain.communityservice.config;

public final class LikeKeys {
    private static final String PREFIX = "campustrain:community:like:";
    private LikeKeys() { }
    public static String users(String type, long id) { return PREFIX + "users:" + type + ":" + id; }
    public static String count(String type, long id) { return PREFIX + "count:" + type + ":" + id; }
    public static String revision(String type, long id) { return PREFIX + "revision:" + type + ":" + id; }
    public static String initialized(String type, long id) { return PREFIX + "init:" + type + ":" + id; }
    public static String lock(String type, long id) { return PREFIX + "lock:" + type + ":" + id; }
    public static String flushLock(String type, long id) { return PREFIX + "flush-lock:" + type + ":" + id; }
    public static String dirty() { return PREFIX + "dirty"; }
    public static String member(String type, long id) { return type + ":" + id; }
}
