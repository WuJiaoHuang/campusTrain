package com.campustrain.enrollmentservice.concurrency;

public final class EnrollmentRedisKeys {

    private static final String PREFIX = "campustrain:enrollment:";

    private EnrollmentRedisKeys() {
    }

    public static String stock(Long trainingId) {
        return PREFIX + "stock:" + trainingId;
    }

    public static String users(Long trainingId) {
        return PREFIX + "users:" + trainingId;
    }

    public static String initialized(Long trainingId) {
        return PREFIX + "init:" + trainingId;
    }

    public static String initializationLock(Long trainingId) {
        return PREFIX + "init-lock:" + trainingId;
    }
}
