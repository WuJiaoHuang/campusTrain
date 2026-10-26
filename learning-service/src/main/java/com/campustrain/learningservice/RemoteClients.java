package com.campustrain.learningservice;

import feign.RequestInterceptor;
import java.time.Instant;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

final class RemoteClients {
    private RemoteClients() {}
    record Result<T>(Integer code, String message, T data) {}
    record Lesson(Long lessonId, Integer lessonType, Integer videoDuration, Long chapterId,
                  Long courseId, Long trainingId, boolean videoBound) {}
    record PlayUrl(String playUrl, Instant expireAt) {}
    record Catalog(Long trainingId, List<Course> courses) {}
    record Course(Long id, List<Chapter> chapters) {}
    record Chapter(Long id, List<LessonNode> lessons) {}
    record LessonNode(Long id) {}
}

@FeignClient(name = "training-service", contextId = "learningTraining")
interface TrainingClient {
    @GetMapping("/internal/learning/lessons/{id}") RemoteClients.Result<RemoteClients.Lesson> lesson(@PathVariable("id") Long id);
    @GetMapping("/internal/learning/lessons/{id}/play-url") RemoteClients.Result<RemoteClients.PlayUrl> playUrl(@PathVariable("id") Long id);
    @GetMapping("/trainings/{id}/catalog") RemoteClients.Result<RemoteClients.Catalog> catalog(@PathVariable("id") Long id);
    @GetMapping("/internal/learning/chapters/{id}/training") RemoteClients.Result<Long> chapterTraining(@PathVariable("id") Long id);
}

@FeignClient(name = "enrollment-service", contextId = "learningEnrollment")
interface EnrollmentClient {
    @GetMapping("/internal/learning/enrollments/{trainingId}/users/{userId}")
    RemoteClients.Result<Boolean> active(@PathVariable("trainingId") Long trainingId, @PathVariable("userId") Long userId);
}

@Configuration
class FeignTraceConfiguration {
    @Bean RequestInterceptor learningTraceInterceptor() {
        return template -> {
            RequestIdentity.Identity identity = RequestIdentity.current();
            if (identity != null && identity.traceId() != null) template.header("X-Trace-Id", identity.traceId());
        };
    }
}
