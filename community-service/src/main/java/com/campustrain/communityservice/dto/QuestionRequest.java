package com.campustrain.communityservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record QuestionRequest(@NotNull Long trainingId, Long courseId, Long lessonId,
                              @NotBlank @Size(max = 200) String title,
                              @NotBlank @Size(max = 10000) String content) { }
