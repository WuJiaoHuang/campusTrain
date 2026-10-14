package com.campustrain.enrollmentservice.remote.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class RemoteTrainingVO {

    private Long id;

    private String title;

    private Integer capacity;

    private Integer enrolledCount;

    private Integer status;

    private LocalDateTime enrollStartTime;

    private LocalDateTime enrollEndTime;
}
