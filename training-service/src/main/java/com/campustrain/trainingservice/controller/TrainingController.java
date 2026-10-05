package com.campustrain.trainingservice.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campustrain.trainingservice.common.Result;
import com.campustrain.trainingservice.dto.TrainingCreateDTO;
import com.campustrain.trainingservice.dto.TrainingPageQueryDTO;
import com.campustrain.trainingservice.dto.TrainingStatusDTO;
import com.campustrain.trainingservice.dto.TrainingUpdateDTO;
import com.campustrain.trainingservice.service.TrainingService;
import com.campustrain.trainingservice.vo.TrainingVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/trainings")
@Tag(name = "培训管理")
public class TrainingController {

    private final TrainingService trainingService;

    @Operation(summary = "创建培训")
    @PostMapping
    public Result<Long> createTraining(@Valid @RequestBody TrainingCreateDTO dto) {
        return Result.success(trainingService.createTraining(dto));
    }

    @Operation(summary = "查询培训详情")
    @GetMapping("/{id}")
    public Result<TrainingVO> getTrainingDetail(@Parameter(description = "培训ID") @PathVariable("id") Long id) {
        return Result.success(trainingService.getTrainingDetail(id));
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
}
