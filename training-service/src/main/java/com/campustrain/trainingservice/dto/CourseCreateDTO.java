package com.campustrain.trainingservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "课程创建参数")
public class CourseCreateDTO {

    @Schema(description = "所属培训ID", example = "1")
    @NotNull(message = "培训ID不能为空")
    private Long trainingId;

    @Schema(description = "课程名称", example = "Java语言核心")
    @NotBlank(message = "课程名称不能为空")
    private String title;

    @Schema(description = "课程简介", example = "系统学习Java基础语法、面向对象和常用API")
    private String description;

    @Schema(description = "排序值，越小越靠前", example = "1")
    @NotNull(message = "排序值不能为空")
    @Min(value = 0, message = "排序值不能小于0")
    private Integer sort;
}
