package com.campustrain.trainingservice.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "课时目录节点")
public class LessonCatalogVO {

    @Schema(description = "课时ID")
    private Long id;

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

    @Schema(description = "视频大小，单位字节")
    private Long videoSize;

    @Schema(description = "视频 Content-Type")
    private String videoContentType;
}
