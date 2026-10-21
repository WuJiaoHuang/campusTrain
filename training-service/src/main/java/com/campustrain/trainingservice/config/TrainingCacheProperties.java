package com.campustrain.trainingservice.config;

import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

@Data
@Component
@RefreshScope
@ConfigurationProperties(prefix = "campustrain.cache")
public class TrainingCacheProperties {
    private Duration detailTtl = Duration.ofMinutes(8);
    private Duration catalogTtl = Duration.ofMinutes(8);
    private Duration nullTtl = Duration.ofSeconds(45);
    private Duration caffeineTtl = Duration.ofSeconds(45);
    private long caffeineMaximumSize = 1_000;
    private Duration ttlJitter = Duration.ofSeconds(30);
}
