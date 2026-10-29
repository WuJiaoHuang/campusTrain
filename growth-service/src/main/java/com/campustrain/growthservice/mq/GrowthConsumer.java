package com.campustrain.growthservice.mq;

import com.campustrain.growthservice.config.RabbitConfig;
import com.campustrain.growthservice.dto.LearningCompletedEvent;
import com.campustrain.growthservice.service.GrowthService;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import com.rabbitmq.client.AMQP;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** ACKs only after the MySQL points fact commits; duplicate event IDs are harmless. */
@Slf4j
@Component
class GrowthConsumer {
    private final GrowthService growthService;
    private final ObjectMapper objectMapper;

    public GrowthConsumer(GrowthService growthService, ObjectMapper objectMapper) {
        this.growthService = growthService;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = RabbitConfig.GROWTH_LEARNING_COMPLETED_QUEUE)
    public void consume(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            growthService.award(objectMapper.readValue(message.getBody(), LearningCompletedEvent.class));
            channel.basicAck(deliveryTag, false);
        } catch (Exception exception) {
            long retries = retryCount(message);
            log.error("growth MQ consumer failed, retries={}", retries, exception);
            if (retries < 3) {
                channel.basicReject(deliveryTag, false);
            } else {
                AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder().deliveryMode(2).build();
                channel.confirmSelect();
                channel.basicPublish("", RabbitConfig.DEAD_QUEUE, properties, message.getBody());
                try {
                    channel.waitForConfirmsOrDie(5000);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("interrupted while confirming growth dead-letter publish", interrupted);
                } catch (TimeoutException timeout) {
                    throw new IOException("timed out while confirming growth dead-letter publish", timeout);
                }
                channel.basicAck(deliveryTag, false);
                log.error("growth MQ message moved to dead queue after retries={}", retries, exception);
            }
        }
    }

    private long retryCount(Message message) {
        Object deaths = message.getMessageProperties().getHeaders().get("x-death");
        if (!(deaths instanceof List<?> list)) return 0;
        for (Object death : list) {
            if (death instanceof Map<?, ?> entry
                    && RabbitConfig.RETRY_QUEUE.equals(entry.get("queue"))
                    && entry.get("count") instanceof Number count) {
                return count.longValue();
            }
        }
        return 0;
    }
}
