package com.campustrain.learningservice;

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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LearningOutboxPublisher {
    private final RabbitTemplate rabbit;
    private final JdbcTemplate jdbc;
    private final Map<String, Long> inFlight = new ConcurrentHashMap<>();

    public LearningOutboxPublisher(RabbitTemplate rabbit, JdbcTemplate jdbc) { this.rabbit = rabbit; this.jdbc = jdbc; }
    @PostConstruct void configure() {
        rabbit.setMandatory(true);
        rabbit.setReturnsCallback(returned -> log.error("learning event returned, eventId={}, reason={}",
                returned.getMessage().getMessageProperties().getHeaders().get("eventId"), returned.getReplyText()));
    }

    @Scheduled(fixedDelayString = "${campustrain.learning.outbox-interval-ms:5000}")
    public void publish() {
        try {
            List<Map<String, Object>> events = jdbc.queryForList("SELECT event_id,payload FROM event_outbox WHERE status='NEW' ORDER BY id LIMIT 50");
            for (Map<String, Object> event : events) send(event);
        } catch (Exception e) { log.error("learning outbox scan failed", e); }
    }

    private void send(Map<String, Object> event) {
        String id = event.get("event_id").toString();
        long now = System.currentTimeMillis();
        Long previous = inFlight.putIfAbsent(id, now);
        if (previous != null) {
            if (now - previous < 60000 || !inFlight.replace(id, previous, now)) return;
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
                        failed(id, reason);
                        log.error("learning event not delivered, eventId={}, reason={}", id, reason, error);
                    } else {
                        jdbc.update("UPDATE event_outbox SET status='SENT',update_time=? WHERE event_id=? AND status='NEW'", LocalDateTime.now(), id);
                        log.info("learning completed event confirmed, eventId={}", id);
                    }
                } catch (Exception e) { log.error("learning outbox confirmation update failed, eventId={}", id, e); }
            });
            rabbit.send("campustrain.business.exchange", "learning.completed",
                    new Message(event.get("payload").toString().getBytes(StandardCharsets.UTF_8), properties), correlation);
        } catch (Exception e) {
            inFlight.remove(id, now);
            try { failed(id, e.toString()); } catch (Exception dbError) { log.error("learning outbox failure update failed, eventId={}", id, dbError); }
            log.error("learning event send failed, eventId={}", id, e);
        }
    }
    private void failed(String id, String reason) {
        jdbc.update("UPDATE event_outbox SET retry_count=retry_count+1,last_error=?,update_time=? WHERE event_id=? AND status='NEW'",
                reason.substring(0, Math.min(reason.length(), 500)), LocalDateTime.now(), id);
    }
}

@Configuration
class LearningSchedulerConfiguration {
    @Bean ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(3); scheduler.setThreadNamePrefix("learning-scheduled-"); return scheduler;
    }
}
