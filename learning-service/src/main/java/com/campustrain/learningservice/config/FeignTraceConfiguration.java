package com.campustrain.learningservice.config;

import com.campustrain.learningservice.context.RequestIdentityContext;
import com.campustrain.learningservice.context.UserIdentity;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignTraceConfiguration {
    @Bean
    RequestInterceptor learningTraceInterceptor() {
        return template -> {
            UserIdentity identity = RequestIdentityContext.current();
            if (identity != null && identity.traceId() != null) {
                template.header("X-Trace-Id", identity.traceId());
            }
        };
    }
}
