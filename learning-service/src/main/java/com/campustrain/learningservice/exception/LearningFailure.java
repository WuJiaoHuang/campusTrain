package com.campustrain.learningservice.exception;

public class LearningFailure extends RuntimeException {
    private final int code;

    public LearningFailure(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
