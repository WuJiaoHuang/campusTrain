package com.campustrain.learningservice.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.campustrain.learningservice.context.RequestIdentityContext;
import com.campustrain.learningservice.context.UserIdentity;
import com.campustrain.learningservice.dto.request.ProgressRequest;
import com.campustrain.learningservice.service.LearningService;
import com.campustrain.learningservice.vo.ApiResult;
import com.campustrain.learningservice.vo.LearningProgressVO;
import com.campustrain.learningservice.vo.LearningStartVO;
import com.campustrain.learningservice.vo.LearningSummaryVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/learning")
@Tag(name = "学习管理")
public class LearningController {
    private final LearningService learningService;

    public LearningController(LearningService learningService) {
        this.learningService = learningService;
    }

    @PostMapping("/start/{lessonId}")
    @Operation(summary = "开始视频学习")
    public ApiResult<LearningStartVO> startLearning(@PathVariable("lessonId") Long lessonId) {
        return ApiResult.ok(learningService.startLearning(lessonId));
    }

    @PostMapping("/report-progress/{lessonId}")
    @Operation(summary = "上报视频学习进度")
    @SentinelResource(value = "learning-progress-report", blockHandler = "handleLearningProgressReportBlock")
    public ResponseEntity<ApiResult<LearningProgressVO>> updateLearningProgress(
            @PathVariable("lessonId") Long lessonId,
            @Valid @RequestBody ProgressRequest request) {
        return ResponseEntity.ok(ApiResult.ok(
                learningService.updateLearningProgress(lessonId, request.progressSeconds())));
    }

    public ResponseEntity<ApiResult<LearningProgressVO>> handleLearningProgressReportBlock(
            Long lessonId, ProgressRequest request, BlockException e) {
        UserIdentity identity = RequestIdentityContext.current();
        Long userId = identity == null ? null : identity.userId();
        String traceId = identity == null ? null : identity.traceId();
        log.warn("Sentinel blocked request, traceId={}, resource=learning-progress-report, userId={}, lessonId={}, blockType={}",
                traceId, userId, lessonId, e.getClass().getSimpleName());
        log.debug("Sentinel block detail, resource=learning-progress-report, userId={}, lessonId={}", userId, lessonId, e);
        if (e instanceof FlowException) {
            return ResponseEntity.status(429).body(ApiResult.fail(429, "学习进度上报过于频繁，请稍后重试"));
        }
        return ResponseEntity.status(503).body(ApiResult.fail(503, "学习服务暂时不可用，请稍后重试"));
    }

    @GetMapping("/get-progress/{lessonId}")
    @Operation(summary = "查询学习进度")
    public ApiResult<LearningProgressVO> getLearningProgress(@PathVariable("lessonId") Long lessonId) {
        return ApiResult.ok(learningService.getLearningProgress(lessonId));
    }

    @PostMapping("/complete/{lessonId}")
    @Operation(summary = "完成图文课时")
    public ApiResult<Void> completeTextLesson(@PathVariable("lessonId") Long lessonId) {
        learningService.completeTextLesson(lessonId);
        return ApiResult.ok(null);
    }

    @GetMapping("/get/training-progress/{trainingId}")
    @Operation(summary = "查询培训完成度")
    public ApiResult<LearningSummaryVO> getTrainingProgress(@PathVariable("trainingId") Long trainingId) {
        return ApiResult.ok(learningService.getTrainingProgress(trainingId));
    }

    @GetMapping("/get/chapter-progress/{chapterId}")
    @Operation(summary = "查询章节完成度")
    public ApiResult<LearningSummaryVO> getChapterProgress(@PathVariable("chapterId") Long chapterId) {
        return ApiResult.ok(learningService.getChapterProgress(chapterId));
    }
}
