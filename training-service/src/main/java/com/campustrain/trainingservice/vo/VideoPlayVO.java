package com.campustrain.trainingservice.vo;

import java.time.Instant;

public record VideoPlayVO(String playUrl, Instant expireAt) {}
