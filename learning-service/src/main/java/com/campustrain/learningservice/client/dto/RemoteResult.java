package com.campustrain.learningservice.client.dto;

public record RemoteResult<T>(Integer code, String message, T data) {
}
