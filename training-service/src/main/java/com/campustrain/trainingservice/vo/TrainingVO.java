package com.campustrain.trainingservice.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class TrainingVO {

    private Long id;

    private String title;

    private String description;

    private String coverUrl;

    private Long teacherId;

    private Integer capacity;

    private Integer enrolledCount;

    private Integer status;

    private LocalDateTime enrollStartTime;

    private LocalDateTime enrollEndTime;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
