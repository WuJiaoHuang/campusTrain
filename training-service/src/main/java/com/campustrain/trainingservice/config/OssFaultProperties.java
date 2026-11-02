package com.campustrain.trainingservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

@Data
@Component
@RefreshScope
@ConfigurationProperties(prefix = "campustrain.oss.fault")
public class OssFaultProperties {
    private boolean enabled = false;
    private boolean playUrlException = false;
}
