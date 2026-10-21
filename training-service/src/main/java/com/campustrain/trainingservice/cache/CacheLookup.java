package com.campustrain.trainingservice.cache;

public record CacheLookup<T>(boolean found, T value) {
    public static <T> CacheLookup<T> found(T value) {
        return new CacheLookup<>(true, value);
    }

    public static <T> CacheLookup<T> absent() {
        return new CacheLookup<>(false, null);
    }
}
