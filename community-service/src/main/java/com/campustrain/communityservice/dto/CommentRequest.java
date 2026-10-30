package com.campustrain.communityservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CommentRequest(@NotBlank String targetType, @NotNull Long targetId,
                             @NotBlank @Size(max = 2000) String content) { }
