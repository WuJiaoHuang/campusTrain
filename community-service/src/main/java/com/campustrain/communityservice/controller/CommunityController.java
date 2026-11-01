package com.campustrain.communityservice.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.campustrain.communityservice.context.UserContext;
import com.campustrain.communityservice.context.UserIdentity;
import com.campustrain.communityservice.dto.AnswerRequest;
import com.campustrain.communityservice.dto.CommentRequest;
import com.campustrain.communityservice.dto.LikeRequest;
import com.campustrain.communityservice.dto.QuestionRequest;
import com.campustrain.communityservice.service.CommunityService;
import com.campustrain.communityservice.service.LikeService;
import com.campustrain.communityservice.vo.AnswerView;
import com.campustrain.communityservice.vo.ApiResult;
import com.campustrain.communityservice.vo.CommentView;
import com.campustrain.communityservice.vo.LikeView;
import com.campustrain.communityservice.vo.PageView;
import com.campustrain.communityservice.vo.QuestionView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/community")
@RequiredArgsConstructor
public class CommunityController {

    private final CommunityService communityService;
    private final LikeService likeService;

    @PostMapping("/create/question")
    public ApiResult<Long> createQuestion(@Valid @RequestBody QuestionRequest request) {
        return ApiResult.success(communityService.createQuestion(request));
    }

    @GetMapping("/list/questions")
    public ApiResult<PageView<QuestionView>> getQuestionPage(
            @RequestParam(name = "trainingId", required = false) Long trainingId,
            @RequestParam(name = "lessonId", required = false) Long lessonId,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return ApiResult.success(communityService.getQuestionPage(trainingId, lessonId, page, size));
    }

    @GetMapping("/get/question/{questionId}")
    public ApiResult<QuestionView> getQuestionDetail(@PathVariable("questionId") long questionId) {
        return ApiResult.success(communityService.getQuestionDetail(questionId));
    }

    @PostMapping("/create/answer/{questionId}")
    public ApiResult<Long> createAnswer(@PathVariable("questionId") long questionId,
                                        @Valid @RequestBody AnswerRequest request) {
        return ApiResult.success(communityService.createAnswer(questionId, request));
    }

    @GetMapping("/list/answers/{questionId}")
    public ApiResult<PageView<AnswerView>> getAnswerPage(
            @PathVariable("questionId") long questionId,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return ApiResult.success(communityService.getAnswerPage(questionId, page, size));
    }

    @PostMapping("/create/comment")
    public ApiResult<Long> createComment(@Valid @RequestBody CommentRequest request) {
        return ApiResult.success(communityService.createComment(request));
    }

    @GetMapping("/list/comments")
    public ApiResult<PageView<CommentView>> getCommentPage(
            @RequestParam("targetType") String targetType,
            @RequestParam("targetId") long targetId,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return ApiResult.success(communityService.getCommentPage(targetType, targetId, page, size));
    }

    @PostMapping("/like")
    @SentinelResource(value = "community-like", blockHandler = "handleLikeBlock")
    public ResponseEntity<ApiResult<LikeView>> likeTarget(@Valid @RequestBody LikeRequest request) {
        return ResponseEntity.ok(ApiResult.success(likeService.likeTarget(request.targetType(), request.targetId())));
    }

    public ResponseEntity<ApiResult<LikeView>> handleLikeBlock(LikeRequest request, BlockException e) {
        String targetType = request == null ? null : request.targetType();
        Long targetId = request == null ? null : request.targetId();
        logSentinelBlock("community-like", targetType, targetId, e);
        if (e instanceof FlowException) {
            return ResponseEntity.status(429).body(ApiResult.fail(429, "点赞操作过于频繁，请稍后重试"));
        }
        return ResponseEntity.status(503).body(ApiResult.fail(503, "社区服务暂时不可用，请稍后重试"));
    }

    @PostMapping("/unlike")
    @SentinelResource(value = "community-unlike", blockHandler = "handleUnlikeBlock")
    public ResponseEntity<ApiResult<LikeView>> unlikeTarget(@Valid @RequestBody LikeRequest request) {
        return ResponseEntity.ok(ApiResult.success(likeService.unlikeTarget(request.targetType(), request.targetId())));
    }

    public ResponseEntity<ApiResult<LikeView>> handleUnlikeBlock(LikeRequest request, BlockException e) {
        String targetType = request == null ? null : request.targetType();
        Long targetId = request == null ? null : request.targetId();
        logSentinelBlock("community-unlike", targetType, targetId, e);
        if (e instanceof FlowException) {
            return ResponseEntity.status(429).body(ApiResult.fail(429, "取消点赞操作过于频繁，请稍后重试"));
        }
        return ResponseEntity.status(503).body(ApiResult.fail(503, "社区服务暂时不可用，请稍后重试"));
    }

    @GetMapping("/get/like-status")
    public ApiResult<LikeView> getLikeState(
            @RequestParam("targetType") String targetType,
            @RequestParam("targetId") long targetId) {
        return ApiResult.success(likeService.getLikeState(targetType, targetId));
    }

    private void logSentinelBlock(String resource, String targetType, Long targetId, BlockException e) {
        UserIdentity identity = UserContext.current();
        Long userId = identity == null ? null : identity.userId();
        String traceId = identity == null ? null : identity.traceId();
        log.warn("Sentinel blocked request, traceId={}, resource={}, userId={}, targetType={}, targetId={}, blockType={}",
                traceId, resource, userId, targetType, targetId, e.getClass().getSimpleName());
        log.debug("Sentinel block detail, resource={}, userId={}, targetType={}, targetId={}",
                resource, userId, targetType, targetId, e);
    }
}
