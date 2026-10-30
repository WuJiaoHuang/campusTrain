package com.campustrain.growthservice.mq;

import com.campustrain.growthservice.config.RabbitConfig;
import com.campustrain.growthservice.dto.CommunityActionEvent;
import com.campustrain.growthservice.service.GrowthService;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class CommunityActionConsumer {
    private final GrowthService growthService;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = RabbitConfig.COMMUNITY_QUEUE)
    public void consume(Message message, Channel channel) throws IOException {
        long tag = message.getMessageProperties().getDeliveryTag();
        try {
            growthService.awardCommunity(objectMapper.readValue(message.getBody(), CommunityActionEvent.class));
            channel.basicAck(tag, false);
        } catch (Exception failure) {
            long retries = retryCount(message);
            log.error("community action consumer failed, retries={}, eventId={}", retries,
                    message.getMessageProperties().getHeaders().get("eventId"), failure);
            if (retries < 3) {
                channel.basicReject(tag, false);
                return;
            }
            AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder().deliveryMode(2).build();
            channel.confirmSelect();
            channel.basicPublish("", RabbitConfig.COMMUNITY_DEAD_QUEUE, properties, message.getBody());
            try {
                channel.waitForConfirmsOrDie(5000);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted while confirming community dead-letter publish", interrupted);
            } catch (TimeoutException timeout) {
                throw new IOException("community dead-letter publish confirmation timed out", timeout);
            }
            channel.basicAck(tag, false);
        }
    }

    private long retryCount(Message message) {
        Object deaths = message.getMessageProperties().getHeaders().get("x-death");
        if (!(deaths instanceof List<?> list)) return 0;
        for (Object death : list) {
            if (death instanceof Map<?, ?> entry
                    && RabbitConfig.COMMUNITY_RETRY_QUEUE.equals(entry.get("queue"))
                    && entry.get("count") instanceof Number count) return count.longValue();
        }
        return 0;
    }
}
