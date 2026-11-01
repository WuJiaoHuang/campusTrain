package com.campustrain.trainingservice.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.alibaba.csp.sentinel.annotation.aspectj.SentinelResourceAspect;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.campustrain.trainingservice.dto.VideoUploadDTO;
import com.campustrain.trainingservice.service.LessonService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

class OssSentinelTest {

    @AfterEach
    void clearRules() {
        FlowRuleManager.loadRules(List.of());
    }

    @Test
    void blockedPlayUrlReturnsVideoServiceUnavailable() {
        LessonService lessonService = mock(LessonService.class);
        AspectJProxyFactory factory = new AspectJProxyFactory(new LessonController(lessonService));
        factory.addAspect(new SentinelResourceAspect());
        LessonController controller = factory.getProxy();
        loadZeroQpsRule("oss-play-url");

        var response = controller.getLessonPlayUrl(9L);

        assertEquals(503, response.getStatusCode().value());
        assertEquals("视频服务暂时不可用，请稍后重试", response.getBody().getMessage());
        verifyNoInteractions(lessonService);
    }

    @Test
    void blockedUploadUrlReturnsVideoServiceUnavailable() {
        LessonService lessonService = mock(LessonService.class);
        AspectJProxyFactory factory = new AspectJProxyFactory(new VideoController(lessonService));
        factory.addAspect(new SentinelResourceAspect());
        VideoController controller = factory.getProxy();
        loadZeroQpsRule("oss-upload-url");
        VideoUploadDTO dto = new VideoUploadDTO();

        var response = controller.createVideoUploadUrl(dto);

        assertEquals(503, response.getStatusCode().value());
        assertEquals("视频服务暂时不可用，请稍后重试", response.getBody().getMessage());
        verifyNoInteractions(lessonService);
    }

    private void loadZeroQpsRule(String resource) {
        FlowRule rule = new FlowRule(resource);
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        rule.setCount(0);
        FlowRuleManager.loadRules(List.of(rule));
    }
}
