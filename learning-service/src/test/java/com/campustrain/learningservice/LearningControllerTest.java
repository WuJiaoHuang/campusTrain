package com.campustrain.learningservice;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import com.campustrain.learningservice.client.EnrollmentClient;
import com.campustrain.learningservice.client.TrainingClient;
import com.campustrain.learningservice.client.dto.Catalog;
import com.campustrain.learningservice.client.dto.Chapter;
import com.campustrain.learningservice.client.dto.Course;
import com.campustrain.learningservice.client.dto.Lesson;
import com.campustrain.learningservice.client.dto.LessonNode;
import com.campustrain.learningservice.client.dto.PlayUrl;
import com.campustrain.learningservice.client.dto.RemoteResult;
import com.campustrain.learningservice.context.RequestIdentity;
import com.campustrain.learningservice.context.RequestIdentityContext;
import com.campustrain.learningservice.controller.LearningController;
import com.campustrain.learningservice.dto.request.ProgressRequest;
import com.campustrain.learningservice.exception.LearningFailure;
import com.campustrain.learningservice.service.LearningProgressStore;
import com.campustrain.learningservice.service.LearningService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class LearningControllerTest {
    private TrainingClient training;
    private EnrollmentClient enrollment;
    private LearningProgressStore store;
    private LearningService service;
    private LearningController controller;
    private final Lesson video = new Lesson(9L, 0, 100, 3L, 4L, 5L, true);

    @BeforeEach void setup() {
        training = mock(TrainingClient.class); enrollment = mock(EnrollmentClient.class); store = mock(LearningProgressStore.class);
        service = new LearningService(training, enrollment, store, 0.9);
        controller = new LearningController(service);
        when(training.lesson(9L)).thenReturn(new RemoteResult<>(200, "success", video));
        when(enrollment.active(5L, 1L)).thenReturn(new RemoteResult<>(200, "success", true));
    }
    private void asStudent(ThrowingAction action) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "1"); request.addHeader("X-User-Role", "STUDENT"); request.addHeader("X-Trace-Id", "test");
        new RequestIdentity().doFilter(request, new MockHttpServletResponse(), (req, resp) -> {
            try { action.run(); } catch (RuntimeException e) { throw e; } catch (Exception e) { throw new RuntimeException(e); }
        });
        assertNull(RequestIdentityContext.current());
    }
    @FunctionalInterface interface ThrowingAction { void run() throws Exception; }

    @Test void startReturnsPrivateUrlAndResume() throws Exception {
        when(store.read(1L, video, "test")).thenReturn(Map.of("progressSeconds", "30", "completed", "0"));
        when(training.playUrl(9L)).thenReturn(new RemoteResult<>(200, "success", new PlayUrl("signed", Instant.parse("2030-01-01T00:00:00Z"))));
        asStudent(() -> { var result = controller.start(9L).data(); assertEquals(30, result.resumePositionSeconds()); assertEquals("signed", result.playUrl()); });
    }
    @Test void noEnrollmentCannotStartOrObtainPlayUrl() throws Exception {
        when(enrollment.active(5L, 1L)).thenReturn(new RemoteResult<>(200, "success", false));
        asStudent(() -> assertEquals(403, assertThrows(LearningFailure.class, () -> controller.start(9L)).getCode()));
        verify(training, never()).playUrl(anyLong());
    }
    @Test void textLessonCannotStartVideo() throws Exception {
        when(training.lesson(9L)).thenReturn(new RemoteResult<>(200, "success", new Lesson(9L, 1, null, 3L, 4L, 5L, false)));
        asStudent(() -> assertEquals(400, assertThrows(LearningFailure.class, () -> controller.start(9L)).getCode()));
        verify(training, never()).playUrl(anyLong());
    }
    @Test void invalidProgressDoesNotReachRedis() throws Exception {
        asStudent(() -> assertEquals(400, assertThrows(LearningFailure.class, () -> controller.update(9L, new ProgressRequest(101))).getCode()));
        verify(store, never()).update(anyLong(), any(), anyInt(), anyDouble(), any());
    }
    @Test void failedEnrollmentDependencyNeverStarts() throws Exception {
        when(enrollment.active(5L, 1L)).thenReturn(new RemoteResult<>(503, "down", null));
        asStudent(() -> assertEquals(503, assertThrows(LearningFailure.class, () -> controller.start(9L)).getCode()));
        verify(training, never()).playUrl(anyLong());
    }
    @Test void chapterAndTrainingCompletionUseOneCatalog() throws Exception {
        var chapter = new Chapter(3L, List.of(new LessonNode(9L), new LessonNode(10L)));
        var other = new Chapter(8L, List.of(new LessonNode(11L)));
        var catalog = new Catalog(5L, List.of(new Course(4L, List.of(chapter, other))));
        when(training.chapterTraining(3L)).thenReturn(new RemoteResult<>(200, "success", 5L));
        when(training.catalog(5L)).thenReturn(new RemoteResult<>(200, "success", catalog));
        when(store.countCompleted(1L, List.of(9L, 10L))).thenReturn(1L);
        when(store.countCompleted(1L, List.of(9L, 10L, 11L))).thenReturn(2L);
        asStudent(() -> {
            var chapterProgress = controller.chapterProgress(3L).data();
            assertEquals(2, chapterProgress.totalLessons()); assertEquals(0.5, chapterProgress.progressRatio());
            var trainingProgress = controller.trainingProgress(5L).data();
            assertEquals(3, trainingProgress.totalLessons()); assertEquals(2, trainingProgress.completedLessons());
        });
        verify(training, times(2)).catalog(5L);
    }
    @Test void textCompletionUsesAuthenticatedIdentityAndIsIdempotent() throws Exception {
        var text = new Lesson(9L, 1, null, 3L, 4L, 5L, false);
        when(training.lesson(9L)).thenReturn(new RemoteResult<>(200, "success", text));
        when(store.complete(1L, text, "test")).thenReturn(1L, 0L);
        asStudent(() -> { assertEquals(200, controller.complete(9L).code()); assertEquals(200, controller.complete(9L).code()); });
        verify(store, times(2)).complete(1L, text, "test");
    }
}
