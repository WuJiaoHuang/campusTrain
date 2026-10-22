package com.campustrain.enrollmentservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "campustrain.enrollment.redis")
public class EnrollmentRedisProperties {

    private String host = "192.168.10.102";
    private int port = 6379;
    private String password = "123456";
    private int connectTimeoutMs = 3000;
}
