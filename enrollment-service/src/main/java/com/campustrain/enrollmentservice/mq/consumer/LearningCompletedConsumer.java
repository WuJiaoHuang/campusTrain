package com.campustrain.enrollmentservice.mq.consumer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campustrain.enrollmentservice.entity.LearningEventAudit;
import com.campustrain.enrollmentservice.entity.MqConsumeRecord;
import com.campustrain.enrollmentservice.mapper.LearningEventAuditMapper;
import com.campustrain.enrollmentservice.mapper.MqConsumeRecordMapper;
import com.campustrain.enrollmentservice.mq.MqConstants;
import com.campustrain.enrollmentservice.mq.event.LearningCompletedEvent;
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
public class LearningCompletedConsumer {
    private static final int MAX_ATTEMPTS = 3;
    private final MqConsumeRecordMapper consumeRecordMapper;
    private final LearningEventAuditMapper auditMapper;
    private final ObjectMapper objectMapper;
    private final PlatformTransactionManager transactionManager;

    public LearningCompletedConsumer(MqConsumeRecordMapper consumeRecordMapper, LearningEventAuditMapper auditMapper,
                                    @Qualifier("mqObjectMapper") ObjectMapper objectMapper, PlatformTransactionManager transactionManager) {
        this.consumeRecordMapper = consumeRecordMapper;
        this.auditMapper = auditMapper;
        this.objectMapper = objectMapper;
        this.transactionManager = transactionManager;
    }

    @RabbitListener(queues = MqConstants.LEARNING_COMPLETED_QUEUE)
    public void consume(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        LearningCompletedEvent event;
        try {
            event = objectMapper.readValue(message.getBody(), LearningCompletedEvent.class);
            processWithRetry(event);
            channel.basicAck(deliveryTag, false);
            log.info("MQ learning completion consumed, eventId={}, traceId={}, userId={}, trainingId={}",
                    event.getEventId(), event.getTraceId(), event.getUserId(), event.getTrainingId());
        } catch (Exception e) {
            log.error("MQ learning completion failed after retries; send to DLQ, eventId={}", header(message, "eventId"), e);
            channel.basicReject(deliveryTag, false);
        }
    }

    private void processWithRetry(LearningCompletedEvent event) throws Exception {
        Exception last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> processOnce(event));
                return;
            } catch (DuplicateKeyException ignored) {
                return;
            } catch (Exception e) {
                last = e;
                log.warn("MQ learning completion retry, eventId={}, attempt={}", event.getEventId(), attempt, e);
            }
        }
        throw last == null ? new IllegalStateException("consumer processing failed") : last;
    }

    private void processOnce(LearningCompletedEvent event) {
        if ("STAGE15_FAIL_ALWAYS".equals(event.getEventType())) throw new IllegalStateException("Stage15 controlled consumer failure");
        if (alreadyConsumed(event.getEventId())) return;
        LocalDateTime now = LocalDateTime.now();
        MqConsumeRecord record = new MqConsumeRecord();
        record.setEventId(event.getEventId()); record.setConsumerName(MqConstants.LEARNING_CONSUMER);
        record.setEventType(event.getEventType()); record.setStatus("DONE"); record.setCreateTime(now); record.setUpdateTime(now);
        consumeRecordMapper.insert(record);
        LearningEventAudit audit = new LearningEventAudit();
        audit.setEventId(event.getEventId()); audit.setUserId(event.getUserId()); audit.setTrainingId(event.getTrainingId());
        audit.setCourseId(event.getCourseId()); audit.setLessonId(event.getLessonId()); audit.setCreateTime(now);
        auditMapper.insert(audit);
    }

    private boolean alreadyConsumed(String eventId) {
        return consumeRecordMapper.selectCount(new LambdaQueryWrapper<MqConsumeRecord>()
                .eq(MqConsumeRecord::getEventId, eventId)
                .eq(MqConsumeRecord::getConsumerName, MqConstants.LEARNING_CONSUMER)) > 0;
    }

    private String header(Message message, String name) {
        Object value = message.getMessageProperties().getHeaders().get(name);
        return value == null ? "unknown" : String.valueOf(value);
    }
}
