package com.campustrain.growthservice.mq;

import com.campustrain.growthservice.config.RabbitConfig;
import com.campustrain.growthservice.dto.CommunityActionEvent;
import com.campustrain.growthservice.service.GrowthService;
import com.rabbitmq.client.Channel;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import tools.jackson.databind.ObjectMapper;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CommunityActionConsumerTest {
    @Test
    void duplicateEventIsAcknowledgedWithoutRetry() throws Exception {
        GrowthService service = mock(GrowthService.class);
        Channel channel = mock(Channel.class);
        MessageProperties props = new MessageProperties();
        props.setDeliveryTag(11L);
        byte[] bytes = ("{\"eventId\":\"duplicate\",\"eventType\":\"COMMUNITY_ACTION\",\"userId\":1001,"
                + "\"targetId\":18002,\"targetType\":\"QUESTION\",\"actionType\":\"QUESTION_CREATED\","
                + "\"occurredAt\":\"2026-10-30T18:00:00+08:00\",\"traceId\":\"trace\"}").getBytes();
        when(service.awardCommunity(any(CommunityActionEvent.class))).thenReturn(false);
        new CommunityActionConsumer(service, new ObjectMapper()).consume(new Message(bytes, props), channel);
        verify(channel).basicAck(11L, false);
        verify(channel, never()).basicReject(anyLong(), anyBoolean());
    }

    @Test
    void failureUsesRetryThenDeadQueue() throws Exception {
        GrowthService service = mock(GrowthService.class);
        Channel channel = mock(Channel.class);
        MessageProperties first = new MessageProperties();
        first.setDeliveryTag(12L);
        new CommunityActionConsumer(service, new ObjectMapper()).consume(new Message("bad".getBytes(), first), channel);
        verify(channel).basicReject(12L, false);
        MessageProperties exhausted = new MessageProperties();
        exhausted.setDeliveryTag(13L);
        exhausted.setHeader("x-death", List.of(Map.of("queue", RabbitConfig.COMMUNITY_RETRY_QUEUE, "count", 3L)));
        byte[] body = "bad".getBytes();
        new CommunityActionConsumer(service, new ObjectMapper()).consume(new Message(body, exhausted), channel);
        verify(channel).basicPublish(eq(""), eq(RabbitConfig.COMMUNITY_DEAD_QUEUE), any(), eq(body));
        verify(channel).waitForConfirmsOrDie(5000);
        verify(channel).basicAck(13L, false);
    }
}
