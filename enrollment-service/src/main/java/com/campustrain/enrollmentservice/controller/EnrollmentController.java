package com.campustrain.enrollmentservice.controller;

import com.campustrain.enrollmentservice.common.Result;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.campustrain.enrollmentservice.context.UserContext;
import com.campustrain.enrollmentservice.dto.EnrollmentCreateDTO;
import com.campustrain.enrollmentservice.service.EnrollmentService;
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

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/enrollments")
@Tag(name = "报名管理")
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    @Operation(summary = "创建报名")
    @PostMapping
    @SentinelResource(value = "enrollment-create", blockHandler = "blocked")
    public ResponseEntity<Result<Long>> enroll(@Valid @RequestBody EnrollmentCreateDTO dto) {
        return ResponseEntity.ok(Result.success(enrollmentService.enroll(dto)));
    }

    public ResponseEntity<Result<Long>> blocked(EnrollmentCreateDTO dto, BlockException e) {
        log.warn("Sentinel blocked, traceId={}, resource=enrollment-create, userId={}, trainingId={}, blockType={}",
                UserContext.getTraceId(), UserContext.getUserId(), dto.getTrainingId(), e.getClass().getSimpleName(), e);
        return e instanceof FlowException
                ? ResponseEntity.status(429).body(Result.fail(429, "报名请求过于频繁，请稍后再试"))
                : ResponseEntity.status(503).body(Result.fail(503, "报名服务暂时不可用，请稍后重试"));
    }
}
