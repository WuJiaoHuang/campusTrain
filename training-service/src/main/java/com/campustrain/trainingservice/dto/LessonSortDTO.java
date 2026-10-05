package com.campustrain.trainingservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "课时排序参数")
public class LessonSortDTO {

    @Schema(description = "课时ID", example = "1")
    @NotNull(message = "课时ID不能为空")
    private Long id;

    @Schema(description = "排序值，越小越靠前", example = "1")
    @NotNull(message = "排序值不能为空")
    @Min(value = 0, message = "排序值不能小于0")
    private Integer sort;
}
