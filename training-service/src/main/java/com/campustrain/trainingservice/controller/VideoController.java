package com.campustrain.trainingservice.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.campustrain.trainingservice.common.Result;
import com.campustrain.trainingservice.context.TraceIdContext;
import com.campustrain.trainingservice.dto.VideoUploadDTO;
import com.campustrain.trainingservice.service.LessonService;
import com.campustrain.trainingservice.vo.VideoUploadVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/video")
@RequiredArgsConstructor
@Tag(name = "视频管理")
public class VideoController {

    private final LessonService lessonService;

    @Operation(summary = "获取视频上传地址")
    @PostMapping("/create/upload-url")
    @SentinelResource(value = "oss-upload-url", blockHandler = "handleOssUploadUrlBlock")
    public ResponseEntity<Result<VideoUploadVO>> createVideoUploadUrl(@Valid @RequestBody VideoUploadDTO dto) {
        return ResponseEntity.ok(Result.success(lessonService.createVideoUploadUrl(dto)));
    }

    public ResponseEntity<Result<VideoUploadVO>> handleOssUploadUrlBlock(VideoUploadDTO dto, BlockException e) {
        String fileName = dto == null ? null : dto.getFileName();
        log.warn("Sentinel blocked request, traceId={}, resource=oss-upload-url, fileName={}, blockType={}",
                TraceIdContext.get(), fileName, e.getClass().getSimpleName());
        log.debug("Sentinel block detail, resource=oss-upload-url, fileName={}", fileName, e);
        if (e instanceof FlowException) {
            return ResponseEntity.status(429).body(Result.fail(429, "视频请求过于频繁，请稍后重试"));
        }
        return ResponseEntity.status(503).body(Result.fail(503, "视频服务暂时不可用，请稍后重试"));
    }
}
