package com.campustrain.learningservice.vo;

public record ApiResult<T>(int code, String message, T data) {
    public static <T> ApiResult<T> ok(T data) {
        return new ApiResult<>(200, "success", data);
    }
}
