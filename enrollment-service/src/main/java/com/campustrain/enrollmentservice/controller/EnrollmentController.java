package com.campustrain.enrollmentservice.controller;

import com.campustrain.enrollmentservice.common.Result;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.campustrain.enrollmentservice.context.UserContext;
import com.campustrain.enrollmentservice.dto.EnrollmentCreateDTO;
import com.campustrain.enrollmentservice.service.EnrollmentService;
import com.campustrain.enrollmentservice.vo.EnrollmentVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/enrollment")
@Tag(name = "报名管理")
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    @Operation(summary = "创建报名")
@PostMapping("/create")
    @SentinelResource(value = "enrollment-create", blockHandler = "blocked")
    public ResponseEntity<Result<Long>> createEnrollment(@Valid @RequestBody EnrollmentCreateDTO dto) {
        return ResponseEntity.ok(Result.success(enrollmentService.createEnrollment(dto)));
    }

    public ResponseEntity<Result<Long>> blocked(EnrollmentCreateDTO dto, BlockException e) {
        log.warn("Sentinel blocked, traceId={}, resource=enrollment-create, userId={}, trainingId={}, blockType={}",
                UserContext.getTraceId(), UserContext.getUserId(), dto.getTrainingId(), e.getClass().getSimpleName());
        log.debug("Sentinel block detail, resource=enrollment-create, trainingId={}", dto.getTrainingId(), e);
        return e instanceof FlowException
                ? ResponseEntity.status(429).body(Result.fail(429, "报名请求过于频繁，请稍后再试"))
                : ResponseEntity.status(503).body(Result.fail(503, "报名服务暂时不可用，请稍后重试"));
    }

@PostMapping("/cancel/{enrollmentId}")
    @Operation(summary = "取消自己的报名")
    public Result<Void> cancelEnrollment(@PathVariable("enrollmentId") Long enrollmentId) {
        enrollmentService.cancelEnrollment(enrollmentId);
        return Result.success();
    }

@GetMapping("/list/mine")
    @Operation(summary = "我的报名")
    public Result<Page<EnrollmentVO>> getMyEnrollments(@RequestParam(value = "page", defaultValue = "1") @Min(1) long page,
            @RequestParam(value = "size", defaultValue = "10") @Min(1) @Max(100) long size,
            @RequestParam(value = "status", required = false) Integer status) {
        return Result.success(enrollmentService.myEnrollments(page, size, status));
    }

@GetMapping("/list/by-training/{trainingId}")
    @Operation(summary = "培训报名名单")
    public Result<Page<EnrollmentVO>> getTrainingRoster(@PathVariable("trainingId") Long trainingId,
            @RequestParam(value = "page", defaultValue = "1") @Min(1) long page,
            @RequestParam(value = "size", defaultValue = "10") @Min(1) @Max(100) long size,
            @RequestParam(value = "status", required = false) Integer status) {
        return Result.success(enrollmentService.trainingEnrollments(trainingId, page, size, status));
    }
}
