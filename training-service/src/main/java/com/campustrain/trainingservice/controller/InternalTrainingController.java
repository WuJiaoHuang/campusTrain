package com.campustrain.trainingservice.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.campustrain.trainingservice.common.Result;
import com.campustrain.trainingservice.context.TraceIdContext;
import com.campustrain.trainingservice.service.TrainingService;
import com.campustrain.trainingservice.vo.TrainingEnrollmentCheckVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/trainings")
public class InternalTrainingController {
    private final TrainingService trainingService;

    @GetMapping("/{id}/enrollment-check")
    @SentinelResource(value = "training-enrollment-check", blockHandler = "blocked")
    public ResponseEntity<Result<TrainingEnrollmentCheckVO>> enrollmentCheck(@PathVariable("id") Long id) {
        return ResponseEntity.ok(Result.success(trainingService.getEnrollmentCheck(id)));
    }

    public ResponseEntity<Result<TrainingEnrollmentCheckVO>> blocked(Long id, BlockException e) {
        log.warn("Sentinel blocked, traceId={}, resource=training-enrollment-check, trainingId={}, blockType={}",
                TraceIdContext.get(), id, e.getClass().getSimpleName());
        log.debug("Sentinel block detail, resource=training-enrollment-check, trainingId={}", id, e);
        return e instanceof FlowException
                ? ResponseEntity.status(429).body(Result.fail(429, "请求过于频繁，请稍后再试"))
                : ResponseEntity.status(503).body(Result.fail(503, "培训服务暂时不可用，请稍后重试"));
    }
}
