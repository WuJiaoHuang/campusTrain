package com.campustrain.trainingservice.cache;

public final class TrainingCacheKeys {
    private static final String PREFIX = "campustrain:training:";

    private TrainingCacheKeys() {
    }

    public static String detailKey(Long trainingId) {
        return PREFIX + "detail:" + trainingId;
    }

    public static String catalogKey(Long trainingId) {
        return PREFIX + "catalog:" + trainingId;
    }
}
