package com.campustrain.enrollmentservice.controller;

import com.campustrain.enrollmentservice.common.Result;
import com.campustrain.enrollmentservice.dto.EnrollmentCreateDTO;
import com.campustrain.enrollmentservice.service.EnrollmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/enrollments")
@Tag(name = "报名管理")
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    @Operation(summary = "创建报名")
    @PostMapping
    public Result<Long> enroll(@Valid @RequestBody EnrollmentCreateDTO dto) {
        return Result.success(enrollmentService.enroll(dto));
    }
}
