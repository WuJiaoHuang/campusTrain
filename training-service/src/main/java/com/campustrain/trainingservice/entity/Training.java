package com.campustrain.trainingservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("training")
public class Training {

    @TableId(type = IdType.AUTO)
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
