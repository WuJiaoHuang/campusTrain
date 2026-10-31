package com.campustrain.communityservice.context;

import com.campustrain.communityservice.client.EnrollmentClient;
import com.campustrain.communityservice.client.TrainingClient;
import com.campustrain.communityservice.client.dto.RemoteResult;
import com.campustrain.communityservice.dto.AnswerRequest;
import com.campustrain.communityservice.dto.CommentRequest;
import com.campustrain.communityservice.dto.QuestionRequest;
import com.campustrain.communityservice.entity.Answer;
import com.campustrain.communityservice.entity.Comment;
import com.campustrain.communityservice.entity.Question;
import com.campustrain.communityservice.mapper.CommunityMapper;
import com.campustrain.communityservice.service.LikeService;
import com.campustrain.communityservice.service.impl.CommunityServiceImpl;
import com.campustrain.communityservice.vo.LikeView;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CommunityServiceTest {
    CommunityMapper communityMapper = mock(CommunityMapper.class);
    TrainingClient trainingClient = mock(TrainingClient.class);
    EnrollmentClient enrollmentClient = mock(EnrollmentClient.class);
    LikeService likeService = mock(LikeService.class);
    CommunityServiceImpl communityServiceImpl = new CommunityServiceImpl(
            communityMapper, trainingClient, enrollmentClient, likeService, new ObjectMapper());

    @BeforeEach
    void identity() { UserContext.set(new UserIdentity(1001L, "STUDENT", "student01", "test-trace")); }
    @AfterEach
    void clear() { UserContext.clear(); }

    @Test
    void questionCreatesOutboxAndCanBeListedAndViewed() {
        when(trainingClient.getTrainingEnrollmentCheck(2003L)).thenReturn(new RemoteResult<>(200, "success", new Object()));
        when(enrollmentClient.hasActiveEnrollment(2003L, 1001L)).thenReturn(new RemoteResult<>(200, "success", true));
        QuestionRequest request = new QuestionRequest(2003L, null, null, "问题", "内容");
        when(communityMapper.insertQuestion(any())).thenAnswer(invocation -> {
            ((com.campustrain.communityservice.entity.QuestionInsert) invocation.getArgument(0)).setId(18002L);
            return 1;
        });
        assertEquals(18002L, communityServiceImpl.createQuestion(request));
        verify(communityMapper).insertOutboxEvent(anyString(), eq("COMMUNITY_ACTION"), eq(18002L),
                argThat(payload -> payload.contains("QUESTION_CREATED") && payload.contains("test-trace")));
        Question row = question();
        when(communityMapper.selectQuestionPage(2003L, null, 0, 20)).thenReturn(List.of(row));
        when(communityMapper.countQuestions(2003L, null)).thenReturn(1L);
        assertEquals(1, communityServiceImpl.getQuestionPage(2003L, null, 1, 20).total());
        when(communityMapper.selectQuestionById(18002L)).thenReturn(row);
        when(communityMapper.selectAnswerPage(18002L, 0, 100)).thenReturn(List.of());
        when(communityMapper.selectCommentPage("QUESTION", 18002L, 0, 100)).thenReturn(List.of());
        when(likeService.getLikeState("QUESTION", 18002L, 0)).thenReturn(new LikeView("QUESTION", 18002L, 3, true, false));
        assertEquals(3, communityServiceImpl.getQuestionDetail(18002L).likeCount());
        assertTrue(communityServiceImpl.getQuestionDetail(18002L).likedByMe());
    }

    @Test
    void answerAndCommentIncrementCountsAndAnswerHasOutbox() throws Exception {
        when(communityMapper.selectQuestionById(18002L)).thenReturn(question());
        when(communityMapper.insertAnswer(any())).thenAnswer(invocation -> {
            ((com.campustrain.communityservice.entity.AnswerInsert) invocation.getArgument(0)).setId(18003L);
            return 1;
        });
        when(communityMapper.incrementQuestionAnswerCount(18002L)).thenReturn(1);
        assertEquals(18003L, communityServiceImpl.createAnswer(18002L, new AnswerRequest("回答")));
        verify(communityMapper).incrementQuestionAnswerCount(18002L);
        verify(communityMapper).insertOutboxEvent(anyString(), eq("COMMUNITY_ACTION"), eq(18003L),
                argThat(payload -> payload.contains("ANSWER_CREATED")));
        when(communityMapper.insertComment(any())).thenAnswer(invocation -> {
            ((com.campustrain.communityservice.entity.CommentInsert) invocation.getArgument(0)).setId(18004L);
            return 1;
        });
        when(communityMapper.incrementQuestionCommentCount(18002L)).thenReturn(1);
        assertEquals(18004L, communityServiceImpl.createComment(new CommentRequest("QUESTION", 18002L, "评论")));
        verify(communityMapper).incrementQuestionCommentCount(18002L);
        assertTrue(CommunityServiceImpl.class.getMethod("createQuestion", QuestionRequest.class).isAnnotationPresent(Transactional.class));
        assertTrue(CommunityServiceImpl.class.getMethod("createAnswer", long.class, AnswerRequest.class).isAnnotationPresent(Transactional.class));
    }

    @Test
    void noEnrollmentCannotCreateQuestion() {
        when(trainingClient.getTrainingEnrollmentCheck(2003L)).thenReturn(new RemoteResult<>(200, "success", new Object()));
        when(enrollmentClient.hasActiveEnrollment(2003L, 1001L)).thenReturn(new RemoteResult<>(200, "success", false));
        var error = assertThrows(com.campustrain.communityservice.exception.CommunityException.class,
                () -> communityServiceImpl.createQuestion(new QuestionRequest(2003L, null, null, "问题", "内容")));
        assertEquals(403, error.status());
        verify(communityMapper, never()).insertQuestion(any());
    }

    private Question question() {
        return new Question(18002L, 2003L, null, null, 1001L, "问题", "内容", 0, 0, LocalDateTime.now());
    }
}
