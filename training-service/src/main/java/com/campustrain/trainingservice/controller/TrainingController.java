package com.campustrain.trainingservice.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.campustrain.trainingservice.common.Result;
import com.campustrain.trainingservice.config.TrainingInstanceInfo;
import com.campustrain.trainingservice.context.TraceIdContext;
import com.campustrain.trainingservice.exception.BusinessException;
import com.campustrain.trainingservice.dto.TrainingCreateDTO;
import com.campustrain.trainingservice.dto.TrainingPageQueryDTO;
import com.campustrain.trainingservice.dto.TrainingStatusDTO;
import com.campustrain.trainingservice.dto.TrainingUpdateDTO;
import com.campustrain.trainingservice.service.TrainingService;
import com.campustrain.trainingservice.vo.TrainingCatalogVO;
import com.campustrain.trainingservice.vo.TrainingVO;
import com.campustrain.trainingservice.vo.TrainingInstanceVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/trainings")
@Tag(name = "培训管理")
public class TrainingController {

    private final TrainingService trainingService;
    private final TrainingInstanceInfo trainingInstanceInfo;

    @Operation(summary = "查询当前培训服务实例")
    @GetMapping("/instance")
    public Result<TrainingInstanceVO> getInstance() {
        return Result.success(trainingInstanceInfo.current());
    }

    @Operation(summary = "创建培训")
    @PostMapping
    public Result<Long> createTraining(@Valid @RequestBody TrainingCreateDTO dto) {
        return Result.success(trainingService.createTraining(dto));
    }



    @Operation(summary = "查询培训详情")
    @GetMapping("/{id}")
    @SentinelResource(value = "training-detail", blockHandler = "handleTrainingDetailBlock",
            exceptionsToTrace = RuntimeException.class, exceptionsToIgnore = BusinessException.class)
    public ResponseEntity<Result<TrainingVO>> getTrainingDetail(@Parameter(description = "培训ID") @PathVariable("id") Long id) {
        return ResponseEntity.ok(Result.success(trainingService.getTrainingDetail(id)));
    }

    public ResponseEntity<Result<TrainingVO>> handleTrainingDetailBlock(Long id, BlockException e) {
        log.warn("Sentinel blocked request, traceId={}, resource=training-detail, trainingId={}, exception={}",
                TraceIdContext.get(), id, e.getClass().getSimpleName());
        log.debug("Sentinel block detail, resource=training-detail, trainingId={}", id, e);
        if (e instanceof FlowException) {
            return ResponseEntity.status(429).body(Result.fail(429, "请求过于频繁，请稍后再试"));
        }
        return ResponseEntity.status(503).body(Result.fail(503, "培训服务暂时不可用，请稍后重试"));
    }

    @Operation(summary = "修改培训")
    @PutMapping
    public Result<Void> updateTraining(@Valid @RequestBody TrainingUpdateDTO dto) {
        trainingService.updateTraining(dto);
        return Result.success();
    }

    @Operation(summary = "分页查询培训")
    @GetMapping("/page")
    public Result<Page<TrainingVO>> pageTrainings(@ParameterObject @Valid TrainingPageQueryDTO dto) {
        return Result.success(trainingService.pageTrainings(dto));
    }

    @Operation(summary = "修改培训状态")
    @PutMapping("/status")
    public Result<Void> changeStatus(@Valid @RequestBody TrainingStatusDTO dto) {
        trainingService.changeStatus(dto.getId(), dto.getStatus());
        return Result.success();
    }

    @Operation(summary = "查询培训课程目录")
    @GetMapping("/{trainingId}/catalog")
    public Result<TrainingCatalogVO> getTrainingCatalog(
            @Parameter(description = "培训ID") @PathVariable("trainingId") Long trainingId
    ) {
        return Result.success(trainingService.getTrainingCatalog(trainingId));
    }
}
