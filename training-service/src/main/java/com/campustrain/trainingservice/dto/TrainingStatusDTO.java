package com.campustrain.trainingservice.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TrainingStatusDTO {

    @NotNull(message = "培训ID不能为空")
    private Long id;

    @NotNull(message = "培训状态不能为空")
    @Min(value = 0, message = "培训状态不合法")
    @Max(value = 3, message = "培训状态不合法")
    private Integer status;
}
