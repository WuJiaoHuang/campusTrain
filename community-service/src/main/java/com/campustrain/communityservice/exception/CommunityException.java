package com.campustrain.communityservice.exception;

public class CommunityException extends RuntimeException {
    private final int status;
    public CommunityException(int status, String message) { super(message); this.status = status; }
    public int status() { return status; }
}
