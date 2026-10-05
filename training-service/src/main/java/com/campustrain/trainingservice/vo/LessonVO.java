package com.campustrain.trainingservice.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@Schema(description = "课时信息")
public class LessonVO {

    @Schema(description = "课时ID")
    private Long id;

    @Schema(description = "所属章节ID")
    private Long chapterId;

    @Schema(description = "课时名称")
    private String title;

    @Schema(description = "课时类型：0视频 1图文")
    private Integer lessonType;

    @Schema(description = "排序值")
    private Integer sort;

    @Schema(description = "视频OSS对象Key")
    private String videoObjectKey;

    @Schema(description = "视频时长，单位秒")
    private Integer videoDuration;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
