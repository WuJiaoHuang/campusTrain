package com.campustrain.communityservice.service;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CommunityOutboxPublisherTest {
    @Test
    void rabbitFailureLeavesOutboxNewAndRecordsRetry() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        when(jdbc.queryForList(anyString())).thenReturn(List.of(Map.of("event_id", "failed-event", "payload", "{}")));
        doThrow(new IllegalStateException("rabbit unavailable")).when(rabbit)
                .send(eq("campustrain.business.exchange"), eq("community.action"), any(Message.class), any(CorrelationData.class));
        new CommunityOutboxPublisher(jdbc, rabbit).publish();
        verify(jdbc).update(contains("retry_count=retry_count+1"), any(), eq("failed-event"));
    }
}
