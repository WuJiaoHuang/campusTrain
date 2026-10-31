package com.campustrain.communityservice.controller;

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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/community")
@RequiredArgsConstructor
public class CommunityController {
    private final CommunityService communityService;
    private final LikeService likeService;

    @PostMapping("/questions")
    public ApiResult<Long> createQuestion(@Valid @RequestBody QuestionRequest request) {
        return ApiResult.success(communityService.createQuestion(request));
    }

    @GetMapping("/questions")
    public ApiResult<PageView<QuestionView>> getQuestionPage(@RequestParam(name = "trainingId", required = false) Long trainingId,
            @RequestParam(name = "lessonId", required = false) Long lessonId,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return ApiResult.success(communityService.getQuestionPage(trainingId, lessonId, page, size));
    }

    @GetMapping("/questions/{id}")
    public ApiResult<QuestionView> getQuestionDetail(@PathVariable("id") long id) {
        return ApiResult.success(communityService.getQuestionDetail(id));
    }

    @PostMapping("/questions/{questionId}/answers")
    public ApiResult<Long> createAnswer(@PathVariable("questionId") long questionId, @Valid @RequestBody AnswerRequest request) {
        return ApiResult.success(communityService.createAnswer(questionId, request));
    }

    @GetMapping("/questions/{questionId}/answers")
    public ApiResult<PageView<AnswerView>> getAnswerPage(@PathVariable("questionId") long questionId,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return ApiResult.success(communityService.getAnswerPage(questionId, page, size));
    }

    @PostMapping("/comments")
    public ApiResult<Long> createComment(@Valid @RequestBody CommentRequest request) {
        return ApiResult.success(communityService.createComment(request));
    }

    @GetMapping("/comments")
    public ApiResult<PageView<CommentView>> getCommentPage(@RequestParam("targetType") String targetType,
            @RequestParam("targetId") long targetId,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return ApiResult.success(communityService.getCommentPage(targetType, targetId, page, size));
    }

    @PostMapping("/likes")
    public ApiResult<LikeView> likeTarget(@Valid @RequestBody LikeRequest request) {
        return ApiResult.success(likeService.likeTarget(request.targetType(), request.targetId()));
    }

    @DeleteMapping("/likes")
    public ApiResult<LikeView> unlikeTarget(@Valid @RequestBody LikeRequest request) {
        return ApiResult.success(likeService.unlikeTarget(request.targetType(), request.targetId()));
    }

    @GetMapping("/likes")
    public ApiResult<LikeView> getLikeState(@RequestParam("targetType") String targetType,
            @RequestParam("targetId") long targetId) {
        return ApiResult.success(likeService.getLikeState(targetType, targetId));
    }
}
