package com.campustrain.enrollmentservice.concurrency;

public enum ReservationResult {
    SUCCESS(0),
    DUPLICATE(1),
    SOLD_OUT(2),
    NOT_INITIALIZED(3);

    private final long code;

    ReservationResult(long code) {
        this.code = code;
    }

    public static ReservationResult fromCode(Number value) {
        if (value != null) {
            for (ReservationResult result : values()) {
                if (result.code == value.longValue()) {
                    return result;
                }
            }
        }
        throw new IllegalStateException("unknown Redis reservation result: " + value);
    }
}
