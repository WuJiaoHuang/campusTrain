package com.campustrain.communityservice.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {
    @Bean(destroyMethod = "shutdown")
    RedissonClient communityRedisson(@Value("${spring.data.redis.host}") String host,
                                     @Value("${spring.data.redis.port}") int port,
                                     @Value("${spring.data.redis.password:}") String password) {
        Config config = new Config();
        var server = config.useSingleServer().setAddress("redis://" + host + ":" + port)
                .setConnectTimeout(2000).setTimeout(2000).setRetryAttempts(1);
        if (password != null && !password.isBlank()) server.setPassword(password);
        return Redisson.create(config);
    }
}
