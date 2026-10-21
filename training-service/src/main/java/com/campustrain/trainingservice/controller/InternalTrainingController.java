package com.campustrain.trainingservice.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.campustrain.trainingservice.common.Result;
import com.campustrain.trainingservice.cache.TrainingCacheService;
import com.campustrain.trainingservice.context.TraceIdContext;
import com.campustrain.trainingservice.service.TrainingService;
import com.campustrain.trainingservice.mapper.TrainingMapper;
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
@RequestMapping("/internal/trainings")
public class InternalTrainingController {
    private final TrainingService trainingService;
    private final TrainingMapper trainingMapper;
    private final TrainingCacheService trainingCacheService;

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

    @PostMapping("/{id}/enrollment-count/increment")
    public Result<Void> increment(@PathVariable("id") Long id) {
        if (trainingMapper.reserveEnrollment(id) != 1) return Result.fail(400, "培训不可报名或名额已满");
        trainingCacheService.evictDetail(id);
        return Result.success();
    }

    @PostMapping("/{id}/enrollment-count/decrement")
    public Result<Void> decrement(@PathVariable("id") Long id) {
        if (trainingMapper.releaseEnrollment(id) != 1) return Result.fail(400, "培训名额计数无法减少");
        trainingCacheService.evictDetail(id);
        return Result.success();
    }

    @PostMapping("/{id}/enrollment-count/restore")
    public Result<Void> restore(@PathVariable("id") Long id) {
        if (trainingMapper.restoreEnrollment(id) != 1) return Result.fail(400, "培训名额计数无法恢复");
        trainingCacheService.evictDetail(id);
        return Result.success();
    }
}
