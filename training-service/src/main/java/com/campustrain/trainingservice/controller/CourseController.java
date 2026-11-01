package com.campustrain.trainingservice.controller;

import com.campustrain.trainingservice.common.Result;
import com.campustrain.trainingservice.dto.CourseCreateDTO;
import com.campustrain.trainingservice.dto.CourseUpdateDTO;
import com.campustrain.trainingservice.service.CourseService;
import com.campustrain.trainingservice.vo.CourseVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
@RequestMapping("/course")
@Tag(name = "课程管理")
public class CourseController {

    private final CourseService courseService;

    @Operation(summary = "创建课程")
@PostMapping("/create")
    public Result<Long> createCourse(@Valid @RequestBody CourseCreateDTO dto) {
        return Result.success(courseService.createCourse(dto));
    }

    @Operation(summary = "修改课程")
@PutMapping("/update")
    public Result<Void> updateCourse(@Valid @RequestBody CourseUpdateDTO dto) {
        courseService.updateCourse(dto);
        return Result.success();
    }

    @Operation(summary = "查询课程详情")
@GetMapping("/get/{courseId}")
    public Result<CourseVO> getCourseDetail(@Parameter(description = "课程ID") @PathVariable("courseId") Long courseId) {
        return Result.success(courseService.getCourseDetail(courseId));
    }

    @Operation(summary = "查询培训下课程")
@GetMapping("/list/by-training/{trainingId}")
    public Result<List<CourseVO>> listByTrainingId(
            @Parameter(description = "培训ID") @PathVariable("trainingId") Long trainingId
    ) {
        return Result.success(courseService.listByTrainingId(trainingId));
    }

    @Operation(summary = "删除课程")
@DeleteMapping("/delete/{courseId}")
    public Result<Void> deleteCourse(@Parameter(description = "课程ID") @PathVariable("courseId") Long courseId) {
        courseService.deleteCourse(courseId);
        return Result.success();
    }
}
