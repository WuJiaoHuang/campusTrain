package com.campustrain.trainingservice.controller;

import com.campustrain.trainingservice.common.Result;
import com.campustrain.trainingservice.dto.ChapterCreateDTO;
import com.campustrain.trainingservice.dto.ChapterSortDTO;
import com.campustrain.trainingservice.dto.ChapterUpdateDTO;
import com.campustrain.trainingservice.service.ChapterService;
import com.campustrain.trainingservice.vo.ChapterVO;
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
@RequestMapping("/chapters")
@Tag(name = "章节管理")
public class ChapterController {

    private final ChapterService chapterService;

    @Operation(summary = "创建章节")
    @PostMapping
    public Result<Long> createChapter(@Valid @RequestBody ChapterCreateDTO dto) {
        return Result.success(chapterService.createChapter(dto));
    }

    @Operation(summary = "修改章节")
    @PutMapping
    public Result<Void> updateChapter(@Valid @RequestBody ChapterUpdateDTO dto) {
        chapterService.updateChapter(dto);
        return Result.success();
    }

    @Operation(summary = "查询课程下章节")
    @GetMapping("/course/{courseId}")
    public Result<List<ChapterVO>> listByCourseId(
            @Parameter(description = "课程ID") @PathVariable("courseId") Long courseId
    ) {
        return Result.success(chapterService.listByCourseId(courseId));
    }

    @Operation(summary = "章节排序")
    @PutMapping("/sort")
    public Result<Void> sortChapters(@RequestBody @NotEmpty(message = "章节排序列表不能为空") List<@Valid ChapterSortDTO> dtoList) {
        chapterService.sortChapters(dtoList);
        return Result.success();
    }

    @Operation(summary = "删除章节")
    @DeleteMapping("/{id}")
    public Result<Void> deleteChapter(@Parameter(description = "章节ID") @PathVariable("id") Long id) {
        chapterService.deleteChapter(id);
        return Result.success();
    }
}
