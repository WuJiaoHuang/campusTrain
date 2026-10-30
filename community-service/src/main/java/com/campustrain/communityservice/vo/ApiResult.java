package com.campustrain.communityservice.vo;

public record ApiResult<T>(int code, String message, T data) {
    public static <T> ApiResult<T> success(T data) { return new ApiResult<>(200, "success", data); }
    public static <T> ApiResult<T> fail(int code, String message) { return new ApiResult<>(code, message, null); }
}
