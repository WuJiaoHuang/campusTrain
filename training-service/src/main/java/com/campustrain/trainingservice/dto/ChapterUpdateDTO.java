package com.campustrain.trainingservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "章节修改参数")
public class ChapterUpdateDTO {

    @Schema(description = "章节ID", example = "1")
    @NotNull(message = "章节ID不能为空")
    private Long id;

    @Schema(description = "章节名称", example = "面向对象编程")
    @NotBlank(message = "章节名称不能为空")
    private String title;

    @Schema(description = "排序值，越小越靠前", example = "2")
    @NotNull(message = "排序值不能为空")
    @Min(value = 0, message = "排序值不能小于0")
    private Integer sort;
}
