package com.campustrain.trainingservice.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campustrain.trainingservice.common.Result;
import com.campustrain.trainingservice.dto.TrainingCreateDTO;
import com.campustrain.trainingservice.dto.TrainingPageQueryDTO;
import com.campustrain.trainingservice.dto.TrainingStatusDTO;
import com.campustrain.trainingservice.dto.TrainingUpdateDTO;
import com.campustrain.trainingservice.service.TrainingService;
import com.campustrain.trainingservice.vo.TrainingVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
public class TrainingController {

    private final TrainingService trainingService;

    @PostMapping
    public Result<Long> createTraining(@Valid @RequestBody TrainingCreateDTO dto) {
        return Result.success(trainingService.createTraining(dto));
    }

    @GetMapping("/{id}")
    public Result<TrainingVO> getTrainingDetail(@PathVariable("id") Long id) {
        return Result.success(trainingService.getTrainingDetail(id));
    }

    @PutMapping
    public Result<Void> updateTraining(@Valid @RequestBody TrainingUpdateDTO dto) {
        trainingService.updateTraining(dto);
        return Result.success();
    }

    @GetMapping("/page")
    public Result<Page<TrainingVO>> pageTrainings(@Valid TrainingPageQueryDTO dto) {
        return Result.success(trainingService.pageTrainings(dto));
    }

    @PutMapping("/status")
    public Result<Void> changeStatus(@Valid @RequestBody TrainingStatusDTO dto) {
        trainingService.changeStatus(dto.getId(), dto.getStatus());
        return Result.success();
    }
}
