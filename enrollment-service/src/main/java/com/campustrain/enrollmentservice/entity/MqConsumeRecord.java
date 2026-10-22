package com.campustrain.enrollmentservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("mq_consume_record")
public class MqConsumeRecord {
    @TableId(type = IdType.AUTO) private Long id;
    private String eventId;
    private String consumerName;
    private String eventType;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
