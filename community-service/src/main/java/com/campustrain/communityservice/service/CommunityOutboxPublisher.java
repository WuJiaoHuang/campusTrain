package com.campustrain.communityservice.service;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommunityOutboxPublisher {
    private final JdbcTemplate jdbc;
    private final RabbitTemplate rabbit;
    private final Map<String, Long> inFlight = new ConcurrentHashMap<>();

    @PostConstruct
    void configure() {
        rabbit.setMandatory(true);
        rabbit.setReturnsCallback(returned -> log.error("community event returned, eventId={}, reason={}",
                returned.getMessage().getMessageProperties().getHeaders().get("eventId"), returned.getReplyText()));
    }

    @Scheduled(fixedDelayString = "${campustrain.community.outbox-interval-ms:5000}")
    public void publish() {
        try {
            for (Map<String, Object> row : jdbc.queryForList(
                    "SELECT event_id,payload FROM event_outbox WHERE status='NEW' ORDER BY id LIMIT 50")) {
                send(row);
            }
        } catch (Exception ex) {
            log.error("community outbox scan failed", ex);
        }
    }

    private void send(Map<String, Object> row) {
        String eventId = row.get("event_id").toString();
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
                        failed(eventId, reason);
                        log.error("community event delivery failed, eventId={}, reason={}", eventId, reason, error);
                    } else {
                        jdbc.update("UPDATE event_outbox SET status='SENT',update_time=NOW() WHERE event_id=? AND status='NEW'", eventId);
                        log.info("community event confirmed, eventId={}", eventId);
                    }
                } catch (Exception ex) {
                    log.error("community outbox confirmation update failed, eventId={}", eventId, ex);
                }
            });
            rabbit.send("campustrain.business.exchange", "community.action",
                    new Message(row.get("payload").toString().getBytes(StandardCharsets.UTF_8), properties), correlation);
        } catch (Exception ex) {
            inFlight.remove(eventId, now);
            try { failed(eventId, ex.toString()); }
            catch (Exception databaseFailure) { log.error("community outbox failure update failed, eventId={}", eventId, databaseFailure); }
            log.error("community event send failed, eventId={}", eventId, ex);
        }
    }

    private void failed(String eventId, String reason) {
        jdbc.update("UPDATE event_outbox SET retry_count=retry_count+1,last_error=?,update_time=NOW() WHERE event_id=? AND status='NEW'",
                reason.substring(0, Math.min(reason.length(), 500)), eventId);
    }
}
