package com.campustrain.trainingservice.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.alibaba.csp.sentinel.annotation.aspectj.SentinelResourceAspect;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
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
        DegradeRuleManager.loadRules(List.of());
    }

    @Test
    void flowBlockedPlayUrlReturnsTooManyRequests() {
        LessonService lessonService = mock(LessonService.class);
        LessonController controller = playUrlController(lessonService);
        loadZeroQpsRule("oss-play-url");

        var response = controller.getLessonPlayUrl(9L);

        assertEquals(429, response.getStatusCode().value());
        assertEquals("视频请求过于频繁，请稍后重试", response.getBody().getMessage());
        verifyNoInteractions(lessonService);
    }

    @Test
    void degradeBlockedPlayUrlReturnsVideoServiceUnavailable() {
        LessonService lessonService = mock(LessonService.class);
        LessonController controller = new LessonController(lessonService);

        var response = controller.handleOssPlayUrlBlock(9L, new DegradeException("degrade"));

        assertEquals(503, response.getStatusCode().value());
        assertEquals("视频服务暂时不可用，请稍后重试", response.getBody().getMessage());
        verifyNoInteractions(lessonService);
    }

    @Test
    void flowBlockedUploadUrlReturnsTooManyRequests() {
        LessonService lessonService = mock(LessonService.class);
        VideoController controller = uploadUrlController(lessonService);
        loadZeroQpsRule("oss-upload-url");

        var response = controller.createVideoUploadUrl(new VideoUploadDTO());

        assertEquals(429, response.getStatusCode().value());
        assertEquals("视频请求过于频繁，请稍后重试", response.getBody().getMessage());
        verifyNoInteractions(lessonService);
    }

    @Test
    void degradeBlockedUploadUrlReturnsVideoServiceUnavailable() {
        LessonService lessonService = mock(LessonService.class);
        VideoController controller = new VideoController(lessonService);

        var response = controller.handleOssUploadUrlBlock(new VideoUploadDTO(), new DegradeException("degrade"));

        assertEquals(503, response.getStatusCode().value());
        assertEquals("视频服务暂时不可用，请稍后重试", response.getBody().getMessage());
        verifyNoInteractions(lessonService);
    }

    private LessonController playUrlController(LessonService lessonService) {
        AspectJProxyFactory factory = new AspectJProxyFactory(new LessonController(lessonService));
        factory.addAspect(new SentinelResourceAspect());
        return factory.getProxy();
    }

    private VideoController uploadUrlController(LessonService lessonService) {
        AspectJProxyFactory factory = new AspectJProxyFactory(new VideoController(lessonService));
        factory.addAspect(new SentinelResourceAspect());
        return factory.getProxy();
    }

    private void loadZeroQpsRule(String resource) {
        FlowRule rule = new FlowRule(resource);
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        rule.setCount(0);
        FlowRuleManager.loadRules(List.of(rule));
    }
}
