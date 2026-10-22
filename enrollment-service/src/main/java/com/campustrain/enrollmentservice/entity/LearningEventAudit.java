package com.campustrain.enrollmentservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("learning_event_audit")
public class LearningEventAudit {
    @TableId(type = IdType.AUTO) private Long id;
    private String eventId;
    private Long userId;
    private Long trainingId;
    private Long courseId;
    private Long lessonId;
    private LocalDateTime createTime;
}
