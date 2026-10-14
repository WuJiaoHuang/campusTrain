package com.campustrain.enrollmentservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("enrollment")
public class Enrollment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long trainingId;

    private Integer status;

    private LocalDateTime enrollTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
