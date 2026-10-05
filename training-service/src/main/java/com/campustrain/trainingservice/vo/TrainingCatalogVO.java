package com.campustrain.trainingservice.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
@Schema(description = "培训课程目录")
public class TrainingCatalogVO {

    @Schema(description = "培训ID")
    private Long trainingId;

    @Schema(description = "培训标题")
    private String trainingTitle;

    @Schema(description = "课程列表")
    private List<CourseCatalogVO> courses = new ArrayList<>();
}
