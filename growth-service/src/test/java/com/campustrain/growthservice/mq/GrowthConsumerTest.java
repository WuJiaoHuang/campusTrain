package com.campustrain.growthservice.mq;

import com.campustrain.growthservice.config.RabbitConfig;
import com.campustrain.growthservice.dto.LearningCompletedEvent;
import com.campustrain.growthservice.service.GrowthService;
import static org.mockito.Mockito.*;

import com.rabbitmq.client.Channel;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import tools.jackson.databind.ObjectMapper;

class GrowthConsumerTest {
    @Test
    void duplicateStillAcknowledgesEvent() throws Exception {
        GrowthService service = mock(GrowthService.class);
        Channel channel = mock(Channel.class);
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(7L);
        Message event = new Message(("{\"eventId\":\"duplicate\",\"eventType\":\"LEARNING_COMPLETED\","+
                "\"userId\":1001,\"trainingId\":2,\"courseId\":3,\"lessonId\":4,"+
                "\"occurredAt\":\"2026-10-15T10:00:00+08:00\",\"traceId\":\"test\"}").getBytes(), properties);
        when(service.award(any(LearningCompletedEvent.class))).thenReturn(false);

        new GrowthConsumer(service, new ObjectMapper()).consume(event, channel);

        verify(channel).basicAck(7L, false);
        verify(channel, never()).basicReject(anyLong(), anyBoolean());
    }

    @Test
    void temporaryFailureRejectsToRetryQueue() throws Exception {
        GrowthService service = mock(GrowthService.class);
        Channel channel = mock(Channel.class);
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(8L);
        Message event = new Message("invalid-json".getBytes(), properties);

        new GrowthConsumer(service, new ObjectMapper()).consume(event, channel);

        verify(channel).basicReject(8L, false);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }

    @Test
    void exhaustedRetriesMoveMessageToDeadQueue() throws Exception {
        GrowthService service = mock(GrowthService.class);
        Channel channel = mock(Channel.class);
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(9L);
        properties.setHeader("x-death", List.of(Map.of("queue", RabbitConfig.RETRY_QUEUE, "count", 3L)));
        Message event = new Message("invalid-json".getBytes(), properties);

        new GrowthConsumer(service, new ObjectMapper()).consume(event, channel);

        verify(channel).basicPublish(eq(""), eq(RabbitConfig.DEAD_QUEUE), any(), eq(event.getBody()));
        verify(channel).waitForConfirmsOrDie(5000);
        verify(channel).basicAck(9L, false);
        verify(channel, never()).basicReject(anyLong(), anyBoolean());
    }
}
