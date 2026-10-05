package com.campustrain.trainingservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "课程修改参数")
public class CourseUpdateDTO {

    @Schema(description = "课程ID", example = "1")
    @NotNull(message = "课程ID不能为空")
    private Long id;

    @Schema(description = "课程名称", example = "Spring Boot企业开发")
    @NotBlank(message = "课程名称不能为空")
    private String title;

    @Schema(description = "课程简介", example = "围绕Spring Boot构建企业级后端服务")
    private String description;

    @Schema(description = "排序值，越小越靠前", example = "2")
    @NotNull(message = "排序值不能为空")
    @Min(value = 0, message = "排序值不能小于0")
    private Integer sort;
}
