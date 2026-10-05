package com.campustrain.trainingservice.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@Schema(description = "章节信息")
public class ChapterVO {

    @Schema(description = "章节ID")
    private Long id;

    @Schema(description = "所属课程ID")
    private Long courseId;

    @Schema(description = "章节名称")
    private String title;

    @Schema(description = "排序值")
    private Integer sort;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
