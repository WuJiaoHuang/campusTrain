package com.campustrain.learningservice.service;

import com.campustrain.learningservice.mapper.LearningOutboxMapper;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LearningOutboxPublisher {
    private final RabbitTemplate rabbitTemplate;
    private final LearningOutboxMapper learningOutboxMapper;
    private final Map<String, Long> inFlight = new ConcurrentHashMap<>();

    public LearningOutboxPublisher(RabbitTemplate rabbitTemplate, LearningOutboxMapper learningOutboxMapper) {
        this.rabbitTemplate = rabbitTemplate;
        this.learningOutboxMapper = learningOutboxMapper;
    }

    @PostConstruct
    void configure() {
        rabbitTemplate.setMandatory(true);
        rabbitTemplate.setReturnsCallback(returned -> log.error("learning event returned, eventId={}, reason={}",
                returned.getMessage().getMessageProperties().getHeaders().get("eventId"), returned.getReplyText()));
    }

    @Scheduled(fixedDelayString = "${campustrain.learning.outbox-interval-ms:5000}")
    public void publishPendingEvents() {
        try {
            List<Map<String, Object>> events = learningOutboxMapper.selectNewEvents();
            for (Map<String, Object> event : events) {
                sendOutboxEvent(event);
            }
        } catch (Exception exception) {
            log.error("learning outbox scan failed", exception);
        }
    }

    private void sendOutboxEvent(Map<String, Object> event) {
        String id = event.get("event_id").toString();
        long now = System.currentTimeMillis();
        Long previous = inFlight.putIfAbsent(id, now);
        if (previous != null) {
            if (now - previous < 60000 || !inFlight.replace(id, previous, now)) {
                return;
            }
        }
        try {
            MessageProperties properties = new MessageProperties();
            properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            properties.setHeader("eventId", id);
            CorrelationData correlation = new CorrelationData(id);
            correlation.getFuture().whenComplete((confirm, error) -> {
                inFlight.remove(id, now);
                try {
                    if (error != null || confirm == null || !confirm.isAck() || correlation.getReturned() != null) {
                        String reason = error != null ? error.toString() : correlation.getReturned() != null
                                ? correlation.getReturned().getReplyText() : "publisher NACK";
                        markOutboxFailed(id, reason);
                        log.error("learning event not delivered, eventId={}, reason={}", id, reason, error);
                    } else {
                        learningOutboxMapper.markSent(id, LocalDateTime.now());
                        log.info("learning completed event confirmed, eventId={}", id);
                    }
                } catch (Exception exception) {
                    log.error("learning outbox confirmation update failed, eventId={}", id, exception);
                }
            });
            rabbitTemplate.send("campustrain.business.exchange", "learning.completed",
                    new Message(event.get("payload").toString().getBytes(StandardCharsets.UTF_8), properties), correlation);
        } catch (Exception exception) {
            inFlight.remove(id, now);
            try {
                markOutboxFailed(id, exception.toString());
            } catch (Exception databaseException) {
                log.error("learning outbox failure update failed, eventId={}", id, databaseException);
            }
            log.error("learning event send failed, eventId={}", id, exception);
        }
    }

    private void markOutboxFailed(String id, String reason) {
        learningOutboxMapper.recordFailure(id, reason.substring(0, Math.min(reason.length(), 500)), LocalDateTime.now());
    }
}
