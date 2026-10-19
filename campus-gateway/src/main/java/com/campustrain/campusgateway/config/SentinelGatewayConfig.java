package com.campustrain.campusgateway.config;

import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.BlockRequestHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerResponse;

@Slf4j
@Configuration
public class SentinelGatewayConfig {
    @Bean
    public BlockRequestHandler gatewayBlockRequestHandler() {
        return (exchange, error) -> {
            String traceId = exchange.getResponse().getHeaders().getFirst("X-Trace-Id");
            log.warn("gateway Sentinel blocked, traceId={}, resource=enrollment-service, path={}, blockType={}",
                    traceId, exchange.getRequest().getPath(), error.getClass().getSimpleName());
            log.debug("gateway Sentinel block detail, path={}", exchange.getRequest().getPath(), error);
            return ServerResponse.status(429).contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{\"code\":429,\"message\":\"报名请求过于频繁，请稍后再试\",\"data\":null}");
        };
    }
}
