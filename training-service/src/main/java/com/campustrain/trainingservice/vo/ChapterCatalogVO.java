package com.campustrain.trainingservice.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
@Schema(description = "章节目录节点")
public class ChapterCatalogVO {

    @Schema(description = "章节ID")
    private Long id;

    @Schema(description = "章节名称")
    private String title;

    @Schema(description = "排序值")
    private Integer sort;

    @Schema(description = "课时列表")
    private List<LessonCatalogVO> lessons = new ArrayList<>();
}
