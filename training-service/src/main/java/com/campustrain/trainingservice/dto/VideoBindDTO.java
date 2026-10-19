package com.campustrain.trainingservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class VideoBindDTO {
    @NotBlank(message = "对象Key不能为空")
    private String objectKey;
    @NotNull(message = "视频大小不能为空")
    private Long size;
    @NotBlank(message = "视频类型不能为空")
    private String contentType;
    @NotNull(message = "视频时长不能为空")
    private Integer duration;
}
