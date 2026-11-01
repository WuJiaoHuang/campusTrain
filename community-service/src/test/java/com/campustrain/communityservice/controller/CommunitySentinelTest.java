package com.campustrain.communityservice.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.alibaba.csp.sentinel.annotation.aspectj.SentinelResourceAspect;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.campustrain.communityservice.dto.LikeRequest;
import com.campustrain.communityservice.service.CommunityService;
import com.campustrain.communityservice.service.LikeService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

class CommunitySentinelTest {

    @AfterEach
    void clearRules() {
        FlowRuleManager.loadRules(List.of());
    }

    @Test
    void blockedLikeNeverCallsLikeService() {
        LikeService likeService = mock(LikeService.class);
        AspectJProxyFactory factory = new AspectJProxyFactory(
                new CommunityController(mock(CommunityService.class), likeService));
        factory.addAspect(new SentinelResourceAspect());
        CommunityController controller = factory.getProxy();
        FlowRule rule = new FlowRule("community-like");
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        rule.setCount(0);
        FlowRuleManager.loadRules(List.of(rule));

        var response = controller.likeTarget(new LikeRequest("QUESTION", 18002L));

        assertEquals(429, response.getStatusCode().value());
        assertEquals(429, response.getBody().code());
        verifyNoInteractions(likeService);
    }

    @Test
    void degradedUnlikeReturns503() {
        CommunityController controller = new CommunityController(mock(CommunityService.class), mock(LikeService.class));

        var response = controller.handleUnlikeBlock(
                new LikeRequest("ANSWER", 18003L), new DegradeException("community-unlike"));

        assertEquals(503, response.getStatusCode().value());
        assertEquals(503, response.getBody().code());
        assertEquals("社区服务暂时不可用，请稍后重试", response.getBody().message());
    }
}
