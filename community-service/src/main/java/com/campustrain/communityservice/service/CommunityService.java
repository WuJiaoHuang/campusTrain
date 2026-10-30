package com.campustrain.communityservice.service;

import com.campustrain.communityservice.dto.AnswerRequest;
import com.campustrain.communityservice.dto.CommentRequest;
import com.campustrain.communityservice.dto.QuestionRequest;
import com.campustrain.communityservice.vo.AnswerView;
import com.campustrain.communityservice.vo.CommentView;
import com.campustrain.communityservice.vo.PageView;
import com.campustrain.communityservice.vo.QuestionView;

public interface CommunityService {
    long createQuestion(QuestionRequest request);
    PageView<QuestionView> questions(Long trainingId, Long lessonId, int page, int size);
    QuestionView question(long id);
    long createAnswer(long questionId, AnswerRequest request);
    PageView<AnswerView> answers(long questionId, int page, int size);
    long createComment(CommentRequest request);
    PageView<CommentView> comments(String type, long targetId, int page, int size);
}
