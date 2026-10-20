package com.campustrain.trainingservice.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.alibaba.csp.sentinel.annotation.aspectj.SentinelResourceAspect;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.campustrain.trainingservice.config.TrainingInstanceInfo;
import com.campustrain.trainingservice.service.TrainingService;
import com.campustrain.trainingservice.vo.TrainingVO;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

class InternalTrainingSentinelTest {
    @AfterEach
    void clearRules() { FlowRuleManager.loadRules(List.of()); }

    @Test
    void internalLimitDoesNotBlockDetailResource() {
        TrainingService service = mock(TrainingService.class);
        when(service.getTrainingDetail(1L)).thenReturn(new TrainingVO());
        AspectJProxyFactory internalFactory = new AspectJProxyFactory(new InternalTrainingController(service, mock(com.campustrain.trainingservice.mapper.TrainingMapper.class)));
        internalFactory.addAspect(new SentinelResourceAspect());
        InternalTrainingController internal = internalFactory.getProxy();
        AspectJProxyFactory detailFactory = new AspectJProxyFactory(new TrainingController(service, mock(TrainingInstanceInfo.class)));
        detailFactory.addAspect(new SentinelResourceAspect());
        TrainingController detail = detailFactory.getProxy();
        FlowRule rule = new FlowRule("training-enrollment-check");
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        rule.setCount(0);
        FlowRuleManager.loadRules(List.of(rule));
        assertEquals(429, internal.enrollmentCheck(1L).getStatusCode().value());
        assertEquals(200, detail.getTrainingDetail(1L).getStatusCode().value());
        verify(service, never()).getEnrollmentCheck(anyLong());
    }
}
