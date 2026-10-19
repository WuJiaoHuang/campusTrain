package com.campustrain.trainingservice.controller;

import com.campustrain.trainingservice.common.Result;
import com.campustrain.trainingservice.dto.VideoUploadDTO;
import com.campustrain.trainingservice.service.LessonService;
import com.campustrain.trainingservice.vo.VideoUploadVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/videos")
@RequiredArgsConstructor
@Tag(name = "视频管理")
public class VideoController {
    private final LessonService lessonService;

    @PostMapping("/upload-url")
    @Operation(summary = "获取视频上传地址")
    public Result<VideoUploadVO> uploadUrl(@Valid @RequestBody VideoUploadDTO dto) {
        return Result.success(lessonService.createUploadUrl(dto));
    }
}
