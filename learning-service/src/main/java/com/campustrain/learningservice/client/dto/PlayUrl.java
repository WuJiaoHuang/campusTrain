package com.campustrain.learningservice.client.dto;

import java.time.Instant;

public record PlayUrl(String playUrl, Instant expireAt) {
}
