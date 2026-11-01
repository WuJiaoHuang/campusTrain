package com.campustrain.trainingservice.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.campustrain.trainingservice.common.Result;
import com.campustrain.trainingservice.context.TraceIdContext;
import com.campustrain.trainingservice.dto.LessonCreateDTO;
import com.campustrain.trainingservice.dto.LessonSortDTO;
import com.campustrain.trainingservice.dto.LessonUpdateDTO;
import com.campustrain.trainingservice.dto.VideoBindDTO;
import com.campustrain.trainingservice.service.LessonService;
import com.campustrain.trainingservice.vo.LessonVO;
import com.campustrain.trainingservice.vo.VideoPlayVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/lesson")
@Tag(name = "课时管理")
public class LessonController {

    private final LessonService lessonService;

    @Operation(summary = "绑定课时视频")
    @PutMapping("/bind-video/{lessonId}")
    public Result<Void> bindVideo(@PathVariable("lessonId") Long lessonId, @Valid @RequestBody VideoBindDTO dto) {
        lessonService.bindVideo(lessonId, dto);
        return Result.success();
    }

    @Operation(summary = "获取视频播放地址")
    @GetMapping("/get/play-url/{lessonId}")
    @SentinelResource(value = "oss-play-url", blockHandler = "handleOssPlayUrlBlock")
    public ResponseEntity<Result<VideoPlayVO>> getLessonPlayUrl(
            @Parameter(description = "课时ID") @PathVariable("lessonId") Long lessonId) {
        return ResponseEntity.ok(Result.success(lessonService.getLessonPlayUrl(lessonId)));
    }

    public ResponseEntity<Result<VideoPlayVO>> handleOssPlayUrlBlock(Long lessonId, BlockException e) {
        log.warn("Sentinel blocked request, traceId={}, resource=oss-play-url, lessonId={}, blockType={}",
                TraceIdContext.get(), lessonId, e.getClass().getSimpleName());
        log.debug("Sentinel block detail, resource=oss-play-url, lessonId={}", lessonId, e);
        return ResponseEntity.status(503).body(Result.fail(503, "视频服务暂时不可用，请稍后重试"));
    }

    @Operation(summary = "创建课时")
    @PostMapping("/create")
    public Result<Long> createLesson(@Valid @RequestBody LessonCreateDTO dto) {
        return Result.success(lessonService.createLesson(dto));
    }

    @Operation(summary = "修改课时")
    @PutMapping("/update")
    public Result<Void> updateLesson(@Valid @RequestBody LessonUpdateDTO dto) {
        lessonService.updateLesson(dto);
        return Result.success();
    }

    @Operation(summary = "查询章节下课时")
    @GetMapping("/list/by-chapter/{chapterId}")
    public Result<List<LessonVO>> listByChapterId(
            @Parameter(description = "章节ID") @PathVariable("chapterId") Long chapterId
    ) {
        return Result.success(lessonService.listByChapterId(chapterId));
    }

    @Operation(summary = "课时排序")
    @PutMapping("/update/sort")
    public Result<Void> sortLessons(@RequestBody @NotEmpty(message = "课时排序列表不能为空") List<@Valid LessonSortDTO> dtoList) {
        lessonService.sortLessons(dtoList);
        return Result.success();
    }

    @Operation(summary = "删除课时")
    @DeleteMapping("/delete/{lessonId}")
    public Result<Void> deleteLesson(@Parameter(description = "课时ID") @PathVariable("lessonId") Long lessonId) {
        lessonService.deleteLesson(lessonId);
        return Result.success();
    }
}
