package com.campustrain.trainingservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "课时创建参数")
public class LessonCreateDTO {

    @Schema(description = "所属章节ID", example = "1")
    @NotNull(message = "章节ID不能为空")
    private Long chapterId;

    @Schema(description = "课时名称", example = "变量与数据类型")
    @NotBlank(message = "课时名称不能为空")
    private String title;

    @Schema(description = "课时类型：0视频 1图文", example = "0")
    @NotNull(message = "课时类型不能为空")
    @Min(value = 0, message = "课时类型只能是0或1")
    @Max(value = 1, message = "课时类型只能是0或1")
    private Integer lessonType;

    @Schema(description = "排序值，越小越靠前", example = "1")
    @NotNull(message = "排序值不能为空")
    @Min(value = 0, message = "排序值不能小于0")
    private Integer sort;

    @Schema(description = "视频OSS对象Key", example = "training/1/course/1/chapter/1/lesson/1.mp4")
    private String videoObjectKey;

    @Schema(description = "视频时长，单位秒", example = "900")
    @Min(value = 0, message = "视频时长不能小于0")
    private Integer videoDuration;
}
