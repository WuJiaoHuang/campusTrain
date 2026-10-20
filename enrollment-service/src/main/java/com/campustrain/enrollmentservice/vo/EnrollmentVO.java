package com.campustrain.enrollmentservice.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class EnrollmentVO {

    private Long enrollmentId;

    private Long userId;

    private Long trainingId;

    private Integer status;

    private LocalDateTime enrollTime;

    private LocalDateTime createTime;
}
