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
    CommunityMapper mapper = mock(CommunityMapper.class);
    TrainingClient training = mock(TrainingClient.class);
    EnrollmentClient enrollment = mock(EnrollmentClient.class);
    LikeService likes = mock(LikeService.class);
    CommunityServiceImpl service = new CommunityServiceImpl(mapper, training, enrollment, likes, new ObjectMapper());

    @BeforeEach
    void identity() { UserContext.set(new UserIdentity(1001L, "STUDENT", "student01", "test-trace")); }
    @AfterEach
    void clear() { UserContext.clear(); }

    @Test
    void questionCreatesOutboxAndCanBeListedAndViewed() {
        when(training.training(2003L)).thenReturn(new RemoteResult<>(200, "success", new Object()));
        when(enrollment.active(2003L, 1001L)).thenReturn(new RemoteResult<>(200, "success", true));
        QuestionRequest request = new QuestionRequest(2003L, null, null, "问题", "内容");
        when(mapper.insertQuestion(1001L, request)).thenReturn(18002L);
        assertEquals(18002L, service.createQuestion(request));
        verify(mapper).insertOutbox(anyString(), eq("COMMUNITY_ACTION"), eq(18002L),
                argThat(payload -> payload.contains("QUESTION_CREATED") && payload.contains("test-trace")));
        Question row = question();
        when(mapper.questions(2003L, null, 1, 20)).thenReturn(List.of(row));
        when(mapper.questionCount(2003L, null)).thenReturn(1L);
        assertEquals(1, service.questions(2003L, null, 1, 20).total());
        when(mapper.question(18002L)).thenReturn(row);
        when(mapper.answers(18002L, 1, 100)).thenReturn(List.of());
        when(mapper.comments("QUESTION", 18002L, 1, 100)).thenReturn(List.of());
        when(likes.read("QUESTION", 18002L, 0)).thenReturn(new LikeView("QUESTION", 18002L, 3, true, false));
        assertEquals(3, service.question(18002L).likeCount());
        assertTrue(service.question(18002L).likedByMe());
    }

    @Test
    void answerAndCommentIncrementCountsAndAnswerHasOutbox() throws Exception {
        when(mapper.question(18002L)).thenReturn(question());
        when(mapper.insertAnswer(18002L, 1001L, "回答")).thenReturn(18003L);
        assertEquals(18003L, service.createAnswer(18002L, new AnswerRequest("回答")));
        verify(mapper).incrementAnswerCount(18002L);
        verify(mapper).insertOutbox(anyString(), eq("COMMUNITY_ACTION"), eq(18003L),
                argThat(payload -> payload.contains("ANSWER_CREATED")));
        when(mapper.insertComment("QUESTION", 18002L, 1001L, "评论")).thenReturn(18004L);
        assertEquals(18004L, service.createComment(new CommentRequest("QUESTION", 18002L, "评论")));
        verify(mapper).incrementCommentCount("QUESTION", 18002L);
        assertTrue(CommunityServiceImpl.class.getMethod("createQuestion", QuestionRequest.class).isAnnotationPresent(Transactional.class));
        assertTrue(CommunityServiceImpl.class.getMethod("createAnswer", long.class, AnswerRequest.class).isAnnotationPresent(Transactional.class));
    }

    @Test
    void noEnrollmentCannotCreateQuestion() {
        when(training.training(2003L)).thenReturn(new RemoteResult<>(200, "success", new Object()));
        when(enrollment.active(2003L, 1001L)).thenReturn(new RemoteResult<>(200, "success", false));
        var error = assertThrows(com.campustrain.communityservice.exception.CommunityException.class,
                () -> service.createQuestion(new QuestionRequest(2003L, null, null, "问题", "内容")));
        assertEquals(403, error.status());
        verify(mapper, never()).insertQuestion(anyLong(), any());
    }

    private Question question() {
        return new Question(18002L, 2003L, null, null, 1001L, "问题", "内容", 0, 0, LocalDateTime.now());
    }
}
