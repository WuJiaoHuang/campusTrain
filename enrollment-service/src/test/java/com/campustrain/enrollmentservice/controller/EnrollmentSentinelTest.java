package com.campustrain.enrollmentservice.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.alibaba.csp.sentinel.annotation.aspectj.SentinelResourceAspect;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.campustrain.enrollmentservice.dto.EnrollmentCreateDTO;
import com.campustrain.enrollmentservice.service.EnrollmentService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

class EnrollmentSentinelTest {
    @AfterEach
    void clearRules() { FlowRuleManager.loadRules(List.of()); }

    @Test
    void blockedRequestNeverCallsEnrollmentService() {
        EnrollmentService service = mock(EnrollmentService.class);
        AspectJProxyFactory factory = new AspectJProxyFactory(new EnrollmentController(service));
        factory.addAspect(new SentinelResourceAspect());
        EnrollmentController controller = factory.getProxy();
        FlowRule rule = new FlowRule("enrollment-create");
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        rule.setCount(0);
        FlowRuleManager.loadRules(List.of(rule));
        EnrollmentCreateDTO dto = new EnrollmentCreateDTO();
        dto.setTrainingId(90001L);
        var response = controller.enroll(dto);
        assertEquals(429, response.getStatusCode().value());
        assertEquals(429, response.getBody().getCode());
        verifyNoInteractions(service);
    }
}
