package com.campustrain.enrollmentservice.remote;

import lombok.Data;

@Data
public class RemoteResult<T> {

    private Integer code;

    private String message;

    private T data;

    public static <T> RemoteResult<T> unavailable(String message) {
        RemoteResult<T> result = new RemoteResult<>();
        result.setCode(503);
        result.setMessage(message);
        return result;
    }
}
