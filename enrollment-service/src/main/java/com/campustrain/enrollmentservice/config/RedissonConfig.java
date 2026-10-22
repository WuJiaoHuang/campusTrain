package com.campustrain.enrollmentservice.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
@EnableConfigurationProperties(EnrollmentRedisProperties.class)
public class RedissonConfig {

    @Bean(destroyMethod = "shutdown")
    public RedissonClient enrollmentRedissonClient(EnrollmentRedisProperties properties) {
        Config config = new Config();
        var singleServer = config.useSingleServer()
                .setAddress("redis://" + properties.getHost() + ":" + properties.getPort())
                .setConnectTimeout(properties.getConnectTimeoutMs());
        if (StringUtils.hasText(properties.getPassword())) {
            singleServer.setPassword(properties.getPassword());
        }
        return Redisson.create(config);
    }
}
