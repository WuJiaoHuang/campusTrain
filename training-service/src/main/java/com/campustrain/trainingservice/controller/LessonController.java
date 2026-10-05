package com.campustrain.trainingservice.controller;

import com.campustrain.trainingservice.common.Result;
import com.campustrain.trainingservice.dto.LessonCreateDTO;
import com.campustrain.trainingservice.dto.LessonSortDTO;
import com.campustrain.trainingservice.dto.LessonUpdateDTO;
import com.campustrain.trainingservice.service.LessonService;
import com.campustrain.trainingservice.vo.LessonVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/lessons")
@Tag(name = "课时管理")
public class LessonController {

    private final LessonService lessonService;

    @Operation(summary = "创建课时")
    @PostMapping
    public Result<Long> createLesson(@Valid @RequestBody LessonCreateDTO dto) {
        return Result.success(lessonService.createLesson(dto));
    }

    @Operation(summary = "修改课时")
    @PutMapping
    public Result<Void> updateLesson(@Valid @RequestBody LessonUpdateDTO dto) {
        lessonService.updateLesson(dto);
        return Result.success();
    }

    @Operation(summary = "查询章节下课时")
    @GetMapping("/chapter/{chapterId}")
    public Result<List<LessonVO>> listByChapterId(
            @Parameter(description = "章节ID") @PathVariable("chapterId") Long chapterId
    ) {
        return Result.success(lessonService.listByChapterId(chapterId));
    }

    @Operation(summary = "课时排序")
    @PutMapping("/sort")
    public Result<Void> sortLessons(@RequestBody @NotEmpty(message = "课时排序列表不能为空") List<@Valid LessonSortDTO> dtoList) {
        lessonService.sortLessons(dtoList);
        return Result.success();
    }

    @Operation(summary = "删除课时")
    @DeleteMapping("/{id}")
    public Result<Void> deleteLesson(@Parameter(description = "课时ID") @PathVariable("id") Long id) {
        lessonService.deleteLesson(id);
        return Result.success();
    }
}
