package com.campustrain.trainingservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

@Data
@Component
@RefreshScope
@ConfigurationProperties(prefix = "campustrain.training.fault")
public class TrainingFaultProperties {
    private boolean delayEnabled = false;
    private long delayMs = 1500;
    private boolean exceptionEnabled = false;
}
