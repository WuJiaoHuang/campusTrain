package com.campustrain.communityservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record LikeRequest(@NotBlank String targetType, @NotNull Long targetId) { }
