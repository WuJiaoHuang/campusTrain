package com.campustrain.trainingservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("course")
public class Course {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long trainingId;

    private String title;

    private String description;

    private Integer sort;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
