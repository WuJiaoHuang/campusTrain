package com.campustrain.communityservice.service;

import java.util.List;
import com.campustrain.communityservice.entity.EventOutbox;
import com.campustrain.communityservice.mapper.CommunityMapper;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CommunityOutboxPublisherTest {
    @Test
    void rabbitFailureLeavesOutboxNewAndRecordsRetry() {
        CommunityMapper communityMapper = mock(CommunityMapper.class);
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        when(communityMapper.selectPendingOutbox(50)).thenReturn(List.of(new EventOutbox("failed-event", "{}")));
        doThrow(new IllegalStateException("rabbit unavailable")).when(rabbitTemplate)
                .send(eq("campustrain.business.exchange"), eq("community.action"), any(Message.class), any(CorrelationData.class));
        new CommunityOutboxPublisher(communityMapper, rabbitTemplate).publishPendingEvents();
        verify(communityMapper).markOutboxFailed(eq("failed-event"), contains("rabbit unavailable"));
    }
}
