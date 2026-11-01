package com.campustrain.trainingservice.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.campustrain.trainingservice.common.Result;
import com.campustrain.trainingservice.context.TraceIdContext;
import com.campustrain.trainingservice.service.InternalTrainingService;
import com.campustrain.trainingservice.service.TrainingService;
import com.campustrain.trainingservice.vo.TrainingEnrollmentCheckVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/training")
public class InternalTrainingController {

    private final TrainingService trainingService;
    private final InternalTrainingService internalTrainingService;

    @GetMapping("/get-enrollment-check/{trainingId}")
    @SentinelResource(value = "training-enrollment-check", blockHandler = "handleEnrollmentCheckBlock")
    public ResponseEntity<Result<TrainingEnrollmentCheckVO>> checkTrainingEnrollment(
            @PathVariable("trainingId") Long trainingId) {
        return ResponseEntity.ok(Result.success(trainingService.getEnrollmentCheck(trainingId)));
    }

    public ResponseEntity<Result<TrainingEnrollmentCheckVO>> handleEnrollmentCheckBlock(
            Long trainingId, BlockException e) {
        log.warn("Sentinel blocked, traceId={}, resource=training-enrollment-check, trainingId={}, blockType={}",
                TraceIdContext.get(), trainingId, e.getClass().getSimpleName());
        log.debug("Sentinel block detail, resource=training-enrollment-check, trainingId={}", trainingId, e);
        return e instanceof FlowException
                ? ResponseEntity.status(429).body(Result.fail(429, "请求过于频繁，请稍后再试"))
                : ResponseEntity.status(503).body(Result.fail(503, "培训服务暂时不可用，请稍后重试"));
    }

    @PostMapping("/increment-enrollment-count/{trainingId}")
    public Result<Void> incrementEnrollmentCount(@PathVariable("trainingId") Long trainingId) {
        if (!internalTrainingService.incrementEnrollmentCount(trainingId)) {
            return Result.fail(400, "培训不可报名或名额已满");
        }
        return Result.success();
    }

    @PostMapping("/decrement-enrollment-count/{trainingId}")
    public Result<Void> decrementEnrollmentCount(@PathVariable("trainingId") Long trainingId) {
        if (!internalTrainingService.decrementEnrollmentCount(trainingId)) {
            return Result.fail(400, "培训名额计数无法减少");
        }
        return Result.success();
    }

    @PostMapping("/restore-enrollment-count/{trainingId}")
    public Result<Void> restoreEnrollmentCount(@PathVariable("trainingId") Long trainingId) {
        if (!internalTrainingService.restoreEnrollmentCount(trainingId)) {
            return Result.fail(400, "培训名额计数无法恢复");
        }
        return Result.success();
    }
}
