package com.campustrain.communityservice.client.dto;

public record RemoteResult<T>(Integer code, String message, T data) { }
