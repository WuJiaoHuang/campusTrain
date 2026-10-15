package com.campustrain.enrollmentservice.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class EnrollmentCreateDTO {

    @NotNull(message = "培训ID不能为空")
    private Long trainingId;
}
