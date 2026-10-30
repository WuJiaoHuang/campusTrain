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
    private final CommunityService community;
    private final LikeService likes;

    @PostMapping("/questions")
    public ApiResult<Long> createQuestion(@Valid @RequestBody QuestionRequest request) {
        return ApiResult.success(community.createQuestion(request));
    }

    @GetMapping("/questions")
    public ApiResult<PageView<QuestionView>> questions(@RequestParam(name = "trainingId", required = false) Long trainingId,
            @RequestParam(name = "lessonId", required = false) Long lessonId,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return ApiResult.success(community.questions(trainingId, lessonId, page, size));
    }

    @GetMapping("/questions/{id}")
    public ApiResult<QuestionView> question(@PathVariable("id") long id) {
        return ApiResult.success(community.question(id));
    }

    @PostMapping("/questions/{questionId}/answers")
    public ApiResult<Long> createAnswer(@PathVariable("questionId") long questionId, @Valid @RequestBody AnswerRequest request) {
        return ApiResult.success(community.createAnswer(questionId, request));
    }

    @GetMapping("/questions/{questionId}/answers")
    public ApiResult<PageView<AnswerView>> answers(@PathVariable("questionId") long questionId,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return ApiResult.success(community.answers(questionId, page, size));
    }

    @PostMapping("/comments")
    public ApiResult<Long> createComment(@Valid @RequestBody CommentRequest request) {
        return ApiResult.success(community.createComment(request));
    }

    @GetMapping("/comments")
    public ApiResult<PageView<CommentView>> comments(@RequestParam("targetType") String targetType,
            @RequestParam("targetId") long targetId,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return ApiResult.success(community.comments(targetType, targetId, page, size));
    }

    @PostMapping("/likes")
    public ApiResult<LikeView> like(@Valid @RequestBody LikeRequest request) {
        return ApiResult.success(likes.like(request.targetType(), request.targetId()));
    }

    @DeleteMapping("/likes")
    public ApiResult<LikeView> unlike(@Valid @RequestBody LikeRequest request) {
        return ApiResult.success(likes.unlike(request.targetType(), request.targetId()));
    }

    @GetMapping("/likes")
    public ApiResult<LikeView> likeState(@RequestParam("targetType") String targetType,
            @RequestParam("targetId") long targetId) {
        return ApiResult.success(likes.readCurrent(targetType, targetId));
    }
}
