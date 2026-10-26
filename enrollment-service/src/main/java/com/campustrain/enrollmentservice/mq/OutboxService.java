package com.campustrain.enrollmentservice.mq;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campustrain.enrollmentservice.entity.EventOutbox;
import com.campustrain.enrollmentservice.mapper.EventOutboxMapper;
import com.campustrain.enrollmentservice.mq.event.EnrollmentSuccessEvent;
import com.campustrain.enrollmentservice.mq.event.LearningCompletedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Qualifier;

@Slf4j
@Service
public class OutboxService {
    private static final String NEW = "NEW";
    private static final String SENT = "SENT";
    private final EventOutboxMapper outboxMapper;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public OutboxService(EventOutboxMapper outboxMapper, RabbitTemplate rabbitTemplate,
                         @Qualifier("mqObjectMapper") ObjectMapper objectMapper) {
        this.outboxMapper = outboxMapper;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void configureRabbitTemplate() {
        rabbitTemplate.setMandatory(true);
        rabbitTemplate.setReturnsCallback(returned -> {
            Object eventId = returned.getMessage().getMessageProperties().getHeaders().get("eventId");
            if (eventId != null) {
                log.error("MQ returned unroutable message, eventId={}, reason={}", eventId, returned.getReplyText());
            }
        });
    }

    public Long createEnrollmentSuccess(Long enrollmentId, Long userId, Long trainingId, String traceId) {
        EnrollmentSuccessEvent event = new EnrollmentSuccessEvent();
        event.setEventId(UUID.randomUUID().toString()); event.setUserId(userId); event.setTrainingId(trainingId);
        event.setEnrollmentId(enrollmentId); event.setOccurredAt(LocalDateTime.now()); event.setTraceId(traceId);
        return create(event.getEventId(), event.getEventType(), enrollmentId, event, MqConstants.ENROLLMENT_SUCCESS_ROUTING_KEY);
    }

    public Long createLearningCompleted(LearningCompletedEvent event) {
        if (event.getEventId() == null) event.setEventId(UUID.randomUUID().toString());
        if (event.getOccurredAt() == null) event.setOccurredAt(LocalDateTime.now());
        return create(event.getEventId(), event.getEventType(), event.getLessonId(), event, MqConstants.LEARNING_COMPLETED_ROUTING_KEY);
    }

    public void dispatch(Long outboxId) {
        EventOutbox outbox = outboxMapper.selectById(outboxId);
        if (outbox == null || !NEW.equals(outbox.getStatus())) return;
        String routingKey = routingKey(outbox.getEventType());
        try {
            MessageProperties properties = new MessageProperties();
            properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            properties.setHeader("eventId", outbox.getEventId());
            properties.setHeader("traceId", traceId(outbox));
            Message message = new Message(outbox.getPayload().getBytes(java.nio.charset.StandardCharsets.UTF_8), properties);
            CorrelationData correlation = new CorrelationData(outbox.getEventId());
            correlation.getFuture().whenComplete((confirm, error) -> {
                if (error != null || confirm == null || !confirm.isAck()) {
                    markFailed(outbox.getEventId(), error == null ? (confirm == null ? "confirm missing" : confirm.getReason()) : error.getMessage());
                    log.error("MQ producer NACK, eventId={}, routingKey={}, traceId={}", outbox.getEventId(), routingKey, traceId(outbox), error);
                    return;
                }
                if (correlation.getReturned() != null) {
                    markFailed(outbox.getEventId(), correlation.getReturned().getReplyText());
                    log.error("MQ confirm ACK with returned message, eventId={}, routingKey={}, traceId={}",
                            outbox.getEventId(), routingKey, traceId(outbox));
                    return;
                }
                markSent(outbox.getEventId());
                log.info("MQ producer confirm ACK, eventId={}, routingKey={}, traceId={}", outbox.getEventId(), routingKey, traceId(outbox));
            });
            rabbitTemplate.send(MqConstants.BUSINESS_EXCHANGE, routingKey, message, correlation);
        } catch (Exception e) {
            markFailed(outbox.getEventId(), e.getMessage());
            log.error("MQ send failed; outbox remains NEW, eventId={}, routingKey={}, traceId={}", outbox.getEventId(), routingKey, traceId(outbox), e);
        }
    }

    @Scheduled(fixedDelayString = "${campustrain.mq.outbox-fixed-delay-ms:5000}")
    public void resendNewOutbox() {
        List<EventOutbox> pending = outboxMapper.selectList(new LambdaQueryWrapper<EventOutbox>().eq(EventOutbox::getStatus, NEW).orderByAsc(EventOutbox::getId).last("LIMIT 50"));
        pending.forEach(item -> dispatch(item.getId()));
    }

    private Long create(String eventId, String eventType, Long aggregateId, Object event, String routingKey) {
        try {
            EventOutbox outbox = new EventOutbox();
            outbox.setEventId(eventId); outbox.setEventType(eventType); outbox.setAggregateId(aggregateId);
            //java转换为json对象，写入payload
            outbox.setPayload(objectMapper.writeValueAsString(event)); outbox.setStatus(NEW); outbox.setRetryCount(0);
            outbox.setCreateTime(LocalDateTime.now()); outbox.setUpdateTime(LocalDateTime.now());
            if (outboxMapper.insert(outbox) != 1) throw new IllegalStateException("outbox write failed");
            return outbox.getId();
        } catch (JsonProcessingException e) { throw new IllegalStateException("outbox event serialization failed", e); }
    }

    private void markSent(String eventId) { outboxMapper.update(null, new LambdaUpdateWrapper<EventOutbox>().eq(EventOutbox::getEventId, eventId).eq(EventOutbox::getStatus, NEW).set(EventOutbox::getStatus, SENT).set(EventOutbox::getUpdateTime, LocalDateTime.now())); }
    private void markFailed(String eventId, String error) { outboxMapper.update(null, new LambdaUpdateWrapper<EventOutbox>().eq(EventOutbox::getEventId, eventId).eq(EventOutbox::getStatus, NEW).setSql("retry_count = retry_count + 1").set(EventOutbox::getLastError, error == null ? "unknown" : error.substring(0, Math.min(500, error.length()))).set(EventOutbox::getUpdateTime, LocalDateTime.now())); }
    private String routingKey(String type) {
        if ("STAGE15_UNROUTABLE".equals(type)) {
            return "stage15.unroutable";
        }
        return type != null && type.startsWith("LEARNING_COMPLETED") || "STAGE15_FAIL_ALWAYS".equals(type)
                ? MqConstants.LEARNING_COMPLETED_ROUTING_KEY : MqConstants.ENROLLMENT_SUCCESS_ROUTING_KEY;
    }
    private String traceId(EventOutbox outbox) { try { return objectMapper.readTree(outbox.getPayload()).path("traceId").asText(); } catch (Exception ignored) { return ""; } }
}
