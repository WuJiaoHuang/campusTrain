package com.campustrain.communityservice.config;

import com.campustrain.communityservice.context.UserContext;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignTraceConfiguration {
    @Bean
    RequestInterceptor traceRequestInterceptor() {
        return template -> {
            if (UserContext.traceId() != null) template.header("X-Trace-Id", UserContext.traceId());
        };
    }
}
