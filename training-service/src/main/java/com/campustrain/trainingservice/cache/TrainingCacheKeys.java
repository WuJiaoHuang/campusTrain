package com.campustrain.trainingservice.cache;

public final class TrainingCacheKeys {
    private static final String PREFIX = "campustrain:training:";

    private TrainingCacheKeys() {
    }

    public static String detail(Long trainingId) {
        return PREFIX + "detail:" + trainingId;
    }

    public static String catalog(Long trainingId) {
        return PREFIX + "catalog:" + trainingId;
    }
}
