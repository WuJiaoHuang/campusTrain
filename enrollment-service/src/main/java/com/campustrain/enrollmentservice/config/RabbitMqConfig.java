package com.campustrain.enrollmentservice.config;

import com.campustrain.enrollmentservice.mq.MqConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;

@Configuration
public class RabbitMqConfig {
    @Bean("mqObjectMapper")
    ObjectMapper mqObjectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }
    @Bean
    DirectExchange campusBusinessExchange() { return new DirectExchange(MqConstants.BUSINESS_EXCHANGE, true, false); }
    @Bean
    DirectExchange campusDlx() { return new DirectExchange(MqConstants.DLX, true, false); }
    @Bean
    Queue enrollmentSuccessQueue() { return QueueBuilder.durable(MqConstants.ENROLLMENT_SUCCESS_QUEUE).deadLetterExchange(MqConstants.DLX).deadLetterRoutingKey(MqConstants.ENROLLMENT_SUCCESS_ROUTING_KEY).build(); }
    @Bean
    Queue enrollmentSuccessDlq() { return QueueBuilder.durable(MqConstants.ENROLLMENT_SUCCESS_DLQ).build(); }
    @Bean
    Binding enrollmentBinding(@Qualifier("enrollmentSuccessQueue") Queue enrollmentSuccessQueue,
                              @Qualifier("campusBusinessExchange") DirectExchange campusBusinessExchange) { return BindingBuilder.bind(enrollmentSuccessQueue).to(campusBusinessExchange).with(MqConstants.ENROLLMENT_SUCCESS_ROUTING_KEY); }
    @Bean
    Binding enrollmentDlqBinding(@Qualifier("enrollmentSuccessDlq") Queue enrollmentSuccessDlq,
                                 @Qualifier("campusDlx") DirectExchange campusDlx) { return BindingBuilder.bind(enrollmentSuccessDlq).to(campusDlx).with(MqConstants.ENROLLMENT_SUCCESS_ROUTING_KEY); }
}
