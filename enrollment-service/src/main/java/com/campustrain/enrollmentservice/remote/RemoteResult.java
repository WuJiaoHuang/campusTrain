package com.campustrain.enrollmentservice.remote;

import lombok.Data;

@Data
public class RemoteResult<T> {

    private Integer code;

    private String message;

    private T data;
}
