package com.campustrain.trainingservice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class TrainingUpdateDTO {

    @NotNull(message = "培训ID不能为空")
    private Long id;

    @NotBlank(message = "培训标题不能为空")
    private String title;

    private String description;

    private String coverUrl;

    @NotNull(message = "教师ID不能为空")
    private Long teacherId;

    @NotNull(message = "最大报名人数不能为空")
    @Min(value = 0, message = "最大报名人数不能小于0")
    private Integer capacity;

    private LocalDateTime enrollStartTime;

    private LocalDateTime enrollEndTime;

    private LocalDateTime startTime;

    private LocalDateTime endTime;
}
