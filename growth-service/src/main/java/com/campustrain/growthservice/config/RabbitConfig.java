package com.campustrain.growthservice.config;

import java.util.Map;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {
    public static final String BUSINESS_EXCHANGE = "campustrain.business.exchange";
    public static final String LEARNING_COMPLETED_ROUTING_KEY = "learning.completed";
    public static final String GROWTH_LEARNING_COMPLETED_QUEUE = "campustrain.growth.learning.completed.queue";
    public static final String RETRY_EXCHANGE = "campustrain.growth.retry.exchange";
    public static final String RETRY_QUEUE = "campustrain.growth.learning.completed.retry.queue";
    public static final String DEAD_QUEUE = "campustrain.growth.learning.completed.dead.queue";
    public static final String COMMUNITY_ROUTING_KEY = "community.action";
    public static final String COMMUNITY_QUEUE = "campustrain.growth.community.action.queue";
    public static final String COMMUNITY_RETRY_QUEUE = "campustrain.growth.community.action.retry.queue";
    public static final String COMMUNITY_DEAD_QUEUE = "campustrain.growth.community.action.dead.queue";
    public static final String COMMUNITY_RETRY_KEY = "growth.community.action.retry";

    @Bean
    Declarables growthQueues() {
        DirectExchange business = new DirectExchange(BUSINESS_EXCHANGE, true, false);
        DirectExchange retry = new DirectExchange(RETRY_EXCHANGE, true, false);
        Queue main = new Queue(GROWTH_LEARNING_COMPLETED_QUEUE, true, false, false, Map.of(
                "x-dead-letter-exchange", RETRY_EXCHANGE,
                "x-dead-letter-routing-key", "growth.learning.completed.retry"));
        Queue delayedRetry = new Queue(RETRY_QUEUE, true, false, false, Map.of(
                "x-message-ttl", 5000,
                "x-dead-letter-exchange", BUSINESS_EXCHANGE,
                "x-dead-letter-routing-key", LEARNING_COMPLETED_ROUTING_KEY));
        Queue dead = new Queue(DEAD_QUEUE, true);
        return new Declarables(business, retry, main, delayedRetry, dead,
                BindingBuilder.bind(main).to(business).with(LEARNING_COMPLETED_ROUTING_KEY),
                BindingBuilder.bind(delayedRetry).to(retry).with("growth.learning.completed.retry"));
    }

    @Bean
    Declarables communityQueues() {
        DirectExchange business = new DirectExchange(BUSINESS_EXCHANGE, true, false);
        DirectExchange retry = new DirectExchange(RETRY_EXCHANGE, true, false);
        Queue main = new Queue(COMMUNITY_QUEUE, true, false, false, Map.of(
                "x-dead-letter-exchange", RETRY_EXCHANGE,
                "x-dead-letter-routing-key", COMMUNITY_RETRY_KEY));
        Queue delayed = new Queue(COMMUNITY_RETRY_QUEUE, true, false, false, Map.of(
                "x-message-ttl", 5000, "x-dead-letter-exchange", BUSINESS_EXCHANGE,
                "x-dead-letter-routing-key", COMMUNITY_ROUTING_KEY));
        Queue dead = new Queue(COMMUNITY_DEAD_QUEUE, true);
        return new Declarables(business, retry, main, delayed, dead,
                BindingBuilder.bind(main).to(business).with(COMMUNITY_ROUTING_KEY),
                BindingBuilder.bind(delayed).to(retry).with(COMMUNITY_RETRY_KEY));
    }
}
