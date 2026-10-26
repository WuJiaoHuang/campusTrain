package com.campustrain.enrollmentservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("event_outbox")
public class EventOutbox {
    @TableId(type = IdType.AUTO) private Long id;
    private String eventId;
    private String eventType;
    private Long aggregateId;
    private String payload;//关联用户对象id,比如enrolleId
    private String status;
    private Integer retryCount;
    private String lastError;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
