package com.campustrain.trainingservice.vo;

import java.time.Instant;

public record VideoUploadVO(String objectKey, String uploadUrl, Instant expireAt) {}
