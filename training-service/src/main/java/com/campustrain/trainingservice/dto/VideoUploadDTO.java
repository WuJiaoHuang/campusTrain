package com.campustrain.trainingservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class VideoUploadDTO {
    @NotBlank(message = "文件名不能为空")
    private String fileName;
    @NotBlank(message = "视频类型不能为空")
    private String contentType;
    @NotNull(message = "视频大小不能为空")
    private Long size;
}
