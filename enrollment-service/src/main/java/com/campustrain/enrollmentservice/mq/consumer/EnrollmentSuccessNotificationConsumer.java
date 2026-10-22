package com.campustrain.enrollmentservice.mq.consumer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campustrain.enrollmentservice.entity.MqConsumeRecord;
import com.campustrain.enrollmentservice.entity.NotificationRecord;
import com.campustrain.enrollmentservice.mapper.MqConsumeRecordMapper;
import com.campustrain.enrollmentservice.mapper.NotificationRecordMapper;
import com.campustrain.enrollmentservice.mq.MqConstants;
import com.campustrain.enrollmentservice.mq.event.EnrollmentSuccessEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Component
public class EnrollmentSuccessNotificationConsumer {
    private static final int MAX_ATTEMPTS = 3;
    private final MqConsumeRecordMapper consumeRecordMapper;
    private final NotificationRecordMapper notificationRecordMapper;
    private final ObjectMapper objectMapper;
    private final PlatformTransactionManager transactionManager;

    public EnrollmentSuccessNotificationConsumer(MqConsumeRecordMapper consumeRecordMapper,
                                                 NotificationRecordMapper notificationRecordMapper,
                                                 @Qualifier("mqObjectMapper") ObjectMapper objectMapper,
                                                 PlatformTransactionManager transactionManager) {
        this.consumeRecordMapper = consumeRecordMapper;
        this.notificationRecordMapper = notificationRecordMapper;
        this.objectMapper = objectMapper;
        this.transactionManager = transactionManager;
    }

    @RabbitListener(queues = MqConstants.ENROLLMENT_SUCCESS_QUEUE)
    public void consume(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        EnrollmentSuccessEvent event;
        try {
            event = objectMapper.readValue(message.getBody(), EnrollmentSuccessEvent.class);
            processWithRetry(event);
            channel.basicAck(deliveryTag, false);
            log.info("MQ enrollment notification consumed, eventId={}, traceId={}, userId={}, trainingId={}",
                    event.getEventId(), event.getTraceId(), event.getUserId(), event.getTrainingId());
        } catch (Exception e) {
            log.error("MQ enrollment notification failed after retries; send to DLQ, eventId={}",
                    header(message, "eventId"), e);
            channel.basicReject(deliveryTag, false);
        }
    }

    private void processWithRetry(EnrollmentSuccessEvent event) throws Exception {
        Exception last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> processOnce(event));
                return;
            } catch (DuplicateKeyException ignored) {
                return;
            } catch (Exception e) {
                last = e;
                log.warn("MQ enrollment notification retry, eventId={}, attempt={}", event.getEventId(), attempt, e);
                if (attempt < MAX_ATTEMPTS) {
                    try {
                        Thread.sleep(100L * attempt);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw interrupted;
                    }
                }
            }
        }
        throw last == null ? new IllegalStateException("consumer processing failed") : last;
    }

    private void processOnce(EnrollmentSuccessEvent event) {
        if (alreadyConsumed(event.getEventId())) return;
        LocalDateTime now = LocalDateTime.now();
        MqConsumeRecord record = new MqConsumeRecord();
        record.setEventId(event.getEventId());
        record.setConsumerName(MqConstants.NOTIFICATION_CONSUMER);
        record.setEventType(event.getEventType());
        record.setStatus("DONE");
        record.setCreateTime(now);
        record.setUpdateTime(now);
        consumeRecordMapper.insert(record);

        NotificationRecord notification = new NotificationRecord();
        notification.setEventId(event.getEventId());
        notification.setUserId(event.getUserId());
        notification.setTrainingId(event.getTrainingId());
        notification.setMessage("报名成功通知");
        notification.setCreateTime(now);
        notificationRecordMapper.insert(notification);
    }

    private boolean alreadyConsumed(String eventId) {
        return consumeRecordMapper.selectCount(new LambdaQueryWrapper<MqConsumeRecord>()
                .eq(MqConsumeRecord::getEventId, eventId)
                .eq(MqConsumeRecord::getConsumerName, MqConstants.NOTIFICATION_CONSUMER)) > 0;
    }

    private String header(Message message, String name) {
        Object value = message.getMessageProperties().getHeaders().get(name);
        return value == null ? "unknown" : String.valueOf(value);
    }
}
