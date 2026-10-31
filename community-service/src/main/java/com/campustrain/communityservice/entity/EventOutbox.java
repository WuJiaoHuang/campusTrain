package com.campustrain.communityservice.entity;

public record EventOutbox(String eventId, String payload) { }
