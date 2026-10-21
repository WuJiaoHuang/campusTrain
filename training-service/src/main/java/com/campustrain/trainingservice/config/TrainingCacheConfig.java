package com.campustrain.trainingservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TrainingCacheConfig {
    @Bean("trainingCacheObjectMapper")
    public ObjectMapper trainingCacheObjectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }
}
