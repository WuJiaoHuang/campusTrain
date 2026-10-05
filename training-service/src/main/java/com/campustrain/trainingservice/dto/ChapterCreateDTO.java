package com.campustrain.trainingservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "章节创建参数")
public class ChapterCreateDTO {

    @Schema(description = "所属课程ID", example = "1")
    @NotNull(message = "课程ID不能为空")
    private Long courseId;

    @Schema(description = "章节名称", example = "Java基础语法")
    @NotBlank(message = "章节名称不能为空")
    private String title;

    @Schema(description = "排序值，越小越靠前", example = "1")
    @NotNull(message = "排序值不能为空")
    @Min(value = 0, message = "排序值不能小于0")
    private Integer sort;
}
