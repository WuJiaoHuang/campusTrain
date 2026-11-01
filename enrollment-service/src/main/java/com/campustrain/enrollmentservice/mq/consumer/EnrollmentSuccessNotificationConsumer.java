package com.campustrain.enrollmentservice.mq.consumer;

import com.campustrain.enrollmentservice.mq.MqConstants;
import com.campustrain.enrollmentservice.mq.event.EnrollmentSuccessEvent;
import com.campustrain.enrollmentservice.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class EnrollmentSuccessNotificationConsumer {
    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;

    public EnrollmentSuccessNotificationConsumer(@Qualifier("mqObjectMapper") ObjectMapper objectMapper,
                                                 NotificationService notificationService) {
        this.objectMapper = objectMapper;
        this.notificationService = notificationService;
    }

    @RabbitListener(queues = MqConstants.ENROLLMENT_SUCCESS_QUEUE)
    public void consume(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            EnrollmentSuccessEvent event = objectMapper.readValue(message.getBody(), EnrollmentSuccessEvent.class);
            notificationService.processEnrollmentSuccess(event);
            channel.basicAck(deliveryTag, false);
            log.info("MQ enrollment notification consumed, eventId={}, traceId={}, userId={}, trainingId={}",
                    event.getEventId(), event.getTraceId(), event.getUserId(), event.getTrainingId());
        } catch (Exception exception) {
            log.error("MQ enrollment notification failed after retries; send to DLQ, eventId={}",
                    header(message, "eventId"), exception);
            channel.basicReject(deliveryTag, false);
        }
    }

    private String header(Message message, String name) {
        Object value = message.getMessageProperties().getHeaders().get(name);
        return value == null ? "unknown" : String.valueOf(value);
    }
}
