package com.campustrain.learningservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.alibaba.csp.sentinel.annotation.aspectj.SentinelResourceAspect;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.campustrain.learningservice.controller.LearningController;
import com.campustrain.learningservice.dto.request.ProgressRequest;
import com.campustrain.learningservice.service.LearningService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

class LearningSentinelTest {

    @AfterEach
    void clearRules() {
        FlowRuleManager.loadRules(List.of());
    }

    @Test
    void blockedProgressReportNeverCallsLearningService() {
        LearningService learningService = mock(LearningService.class);
        AspectJProxyFactory factory = new AspectJProxyFactory(new LearningController(learningService));
        factory.addAspect(new SentinelResourceAspect());
        LearningController controller = factory.getProxy();
        FlowRule rule = new FlowRule("learning-progress-report");
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        rule.setCount(0);
        FlowRuleManager.loadRules(List.of(rule));

        var response = controller.updateLearningProgress(9L, new ProgressRequest(10));

        assertEquals(429, response.getStatusCode().value());
        assertEquals(429, response.getBody().code());
        verifyNoInteractions(learningService);
    }
}
