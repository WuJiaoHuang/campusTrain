package com.campustrain.trainingservice.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@Schema(description = "课程信息")
public class CourseVO {

    @Schema(description = "课程ID")
    private Long id;

    @Schema(description = "所属培训ID")
    private Long trainingId;

    @Schema(description = "课程名称")
    private String title;

    @Schema(description = "课程简介")
    private String description;

    @Schema(description = "排序值")
    private Integer sort;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
