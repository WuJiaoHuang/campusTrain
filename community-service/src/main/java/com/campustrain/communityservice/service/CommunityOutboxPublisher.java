package com.campustrain.communityservice.service;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.campustrain.communityservice.entity.EventOutbox;
import com.campustrain.communityservice.mapper.CommunityMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommunityOutboxPublisher {
    private final CommunityMapper communityMapper;
    private final RabbitTemplate rabbitTemplate;
    private final Map<String, Long> inFlight = new ConcurrentHashMap<>();

    @PostConstruct
    void configure() {
        rabbitTemplate.setMandatory(true);
        rabbitTemplate.setReturnsCallback(returned -> log.error("community event returned, eventId={}, reason={}",
                returned.getMessage().getMessageProperties().getHeaders().get("eventId"), returned.getReplyText()));
    }

    @Scheduled(fixedDelayString = "${campustrain.community.outbox-interval-ms:5000}")
    public void publishPendingEvents() {
        try {
            for (EventOutbox eventOutbox : communityMapper.selectPendingOutbox(50)) {
                sendOutboxEvent(eventOutbox);
            }
        } catch (Exception ex) {
            log.error("community outbox scan failed", ex);
        }
    }

    private void sendOutboxEvent(EventOutbox eventOutbox) {
        String eventId = eventOutbox.eventId();
        long now = System.currentTimeMillis();
        Long previous = inFlight.putIfAbsent(eventId, now);
        if (previous != null && (now - previous < 60000 || !inFlight.replace(eventId, previous, now))) return;
        try {
            MessageProperties properties = new MessageProperties();
            properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            properties.setHeader("eventId", eventId);
            CorrelationData correlation = new CorrelationData(eventId);
            correlation.getFuture().whenComplete((confirm, error) -> {
                inFlight.remove(eventId, now);
                try {
                    if (error != null || confirm == null || !confirm.isAck() || correlation.getReturned() != null) {
                        String reason = error != null ? error.toString() : correlation.getReturned() != null
                                ? correlation.getReturned().getReplyText() : "publisher NACK";
                        markOutboxFailed(eventId, reason);
                        log.error("community event delivery failed, eventId={}, reason={}", eventId, reason, error);
                    } else {
                        communityMapper.markOutboxSent(eventId);
                        log.info("community event confirmed, eventId={}", eventId);
                    }
                } catch (Exception ex) {
                    log.error("community outbox confirmation update failed, eventId={}", eventId, ex);
                }
            });
            rabbitTemplate.send("campustrain.business.exchange", "community.action",
                    new Message(eventOutbox.payload().getBytes(StandardCharsets.UTF_8), properties), correlation);
        } catch (Exception ex) {
            inFlight.remove(eventId, now);
            try { markOutboxFailed(eventId, ex.toString()); }
            catch (Exception databaseFailure) { log.error("community outbox failure update failed, eventId={}", eventId, databaseFailure); }
            log.error("community event send failed, eventId={}", eventId, ex);
        }
    }

    private void markOutboxFailed(String eventId, String reason) {
        communityMapper.markOutboxFailed(eventId, reason.substring(0, Math.min(reason.length(), 500)));
    }
}
