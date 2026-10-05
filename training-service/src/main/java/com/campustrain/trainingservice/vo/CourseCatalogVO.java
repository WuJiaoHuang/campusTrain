package com.campustrain.trainingservice.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
@Schema(description = "课程目录节点")
public class CourseCatalogVO {

    @Schema(description = "课程ID")
    private Long id;

    @Schema(description = "课程名称")
    private String title;

    @Schema(description = "课程简介")
    private String description;

    @Schema(description = "排序值")
    private Integer sort;

    @Schema(description = "章节列表")
    private List<ChapterCatalogVO> chapters = new ArrayList<>();
}
