package com.campustrain.trainingservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("lesson")
public class Lesson {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long chapterId;

    private String title;

    private Integer lessonType;

    private Integer sort;

    private String videoObjectKey;

    private Integer videoDuration;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
