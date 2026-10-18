package com.campustrain.campusgateway.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

class SentinelGatewayConfigTest {
    @Test
    void blockHandlerReturns429() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/enrollments"));
        var response = new SentinelGatewayConfig().gatewayBlockRequestHandler()
                .handleRequest(exchange, new FlowException("enrollment-service")).block();
        assertEquals(429, response.statusCode().value());
    }
}
