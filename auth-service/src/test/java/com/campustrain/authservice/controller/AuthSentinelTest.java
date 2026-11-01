package com.campustrain.authservice.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.alibaba.csp.sentinel.annotation.aspectj.SentinelResourceAspect;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.campustrain.authservice.dto.LoginDTO;
import com.campustrain.authservice.service.AuthService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

class AuthSentinelTest {

    @AfterEach
    void clearRules() {
        FlowRuleManager.loadRules(List.of());
    }

    @Test
    void blockedLoginNeverCallsAuthService() {
        AuthService authService = mock(AuthService.class);
        AspectJProxyFactory factory = new AspectJProxyFactory(new AuthController(authService));
        factory.addAspect(new SentinelResourceAspect());
        AuthController controller = factory.getProxy();
        FlowRule rule = new FlowRule("auth-login");
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        rule.setCount(0);
        FlowRuleManager.loadRules(List.of(rule));
        LoginDTO dto = new LoginDTO();
        dto.setUsername("student");
        dto.setPassword("password");

        var response = controller.login(dto);

        assertEquals(429, response.getStatusCode().value());
        assertEquals(429, response.getBody().getCode());
        assertEquals("登录请求过于频繁，请稍后重试", response.getBody().getMessage());
        verifyNoInteractions(authService);
    }
}
