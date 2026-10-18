package com.campustrain.trainingservice.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.alibaba.csp.sentinel.annotation.aspectj.SentinelResourceAspect;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.campustrain.trainingservice.config.TrainingFaultProperties;
import com.campustrain.trainingservice.config.TrainingInstanceInfo;
import com.campustrain.trainingservice.controller.TrainingController;
import com.campustrain.trainingservice.exception.BusinessException;
import com.campustrain.trainingservice.exception.TrainingFaultException;
import com.campustrain.trainingservice.vo.TrainingVO;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

class Stage10Test {
    @AfterEach
    void clearRules() {
        FlowRuleManager.loadRules(List.of());
        DegradeRuleManager.loadRules(List.of());
    }

    private TrainingController proxy(TrainingService service) {
        AspectJProxyFactory factory = new AspectJProxyFactory(new TrainingController(service, mock(TrainingInstanceInfo.class)));
        factory.addAspect(new SentinelResourceAspect());
        return factory.getProxy();
    }

    @Test
    void faultDefaultsAreDisabled() {
        TrainingFaultProperties properties = new TrainingFaultProperties();
        assertFalse(properties.isDelayEnabled());
        assertFalse(properties.isExceptionEnabled());
        assertEquals(1500, properties.getDelayMs());
    }

    @Test
    void normalAndBlockedResponsesAreDistinct() {
        TrainingService service = mock(TrainingService.class);
        TrainingController controller = new TrainingController(service, mock(TrainingInstanceInfo.class));
        TrainingVO value = new TrainingVO();
        when(service.getTrainingDetail(1L)).thenReturn(value);
        assertSame(value, controller.getTrainingDetail(1L).getBody().getData());
        assertEquals(429, controller.handleTrainingDetailBlock(1L, new FlowException("flow")).getStatusCode().value());
        assertEquals(503, controller.handleTrainingDetailBlock(1L, new DegradeException("degrade")).getStatusCode().value());
    }

    @Test
    void annotationInvokesFlowBlockHandler() {
        TrainingService service = mock(TrainingService.class);
        when(service.getTrainingDetail(1L)).thenReturn(new TrainingVO());
        TrainingController controller = proxy(service);
        FlowRule rule = new FlowRule("training-detail");
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        rule.setCount(1);
        FlowRuleManager.loadRules(List.of(rule));
        assertEquals(200, controller.getTrainingDetail(1L).getStatusCode().value());
        var blocked = controller.getTrainingDetail(1L);
        assertEquals(429, blocked.getStatusCode().value());
        assertEquals(429, blocked.getBody().getCode());
        verify(service, times(1)).getTrainingDetail(1L);
    }

    @Test
    void annotationTracesFaultExceptionAndOpensCircuit() {
        TrainingService service = mock(TrainingService.class);
        when(service.getTrainingDetail(1L)).thenThrow(new TrainingFaultException("injected"));
        TrainingController controller = proxy(service);
        DegradeRule rule = new DegradeRule("training-detail")
                .setGrade(RuleConstant.DEGRADE_GRADE_EXCEPTION_RATIO)
                .setCount(0.5).setMinRequestAmount(5).setStatIntervalMs(10000).setTimeWindow(5);
        DegradeRuleManager.loadRules(List.of(rule));
        for (int i = 0; i < 5; i++) {
            assertThrows(TrainingFaultException.class, () -> controller.getTrainingDetail(1L));
        }
        var blocked = controller.getTrainingDetail(1L);
        assertEquals(503, blocked.getStatusCode().value());
        assertEquals(503, blocked.getBody().getCode());
    }

    @Test
    void businessExceptionsAreNotCountedAsFaults() {
        TrainingService service = mock(TrainingService.class);
        when(service.getTrainingDetail(1L)).thenThrow(new BusinessException("not found"));
        TrainingController controller = proxy(service);
        DegradeRule rule = new DegradeRule("training-detail")
                .setGrade(RuleConstant.DEGRADE_GRADE_EXCEPTION_RATIO)
                .setCount(0.5).setMinRequestAmount(5).setStatIntervalMs(10000).setTimeWindow(5);
        DegradeRuleManager.loadRules(List.of(rule));
        for (int i = 0; i < 6; i++) {
            assertThrows(BusinessException.class, () -> controller.getTrainingDetail(1L));
        }
    }
}
