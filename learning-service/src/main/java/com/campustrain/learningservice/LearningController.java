package com.campustrain.learningservice;

import feign.FeignException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestController
@RequestMapping("/learning")
public class LearningController {
    private final TrainingClient training;
    private final EnrollmentClient enrollment;
    private final LearningProgressStore progress;
    private final double ratio;

    public LearningController(TrainingClient training, EnrollmentClient enrollment, LearningProgressStore progress,
                              @Value("${campustrain.learning.complete-ratio:0.90}") double ratio) {
        if (ratio <= 0 || ratio > 1) throw new IllegalArgumentException("invalid learning complete ratio");
        this.training = training; this.enrollment = enrollment; this.progress = progress; this.ratio = ratio;
    }
    public record Result<T>(int code, String message, T data) {
        static <T> Result<T> ok(T data) { return new Result<>(200, "success", data); }
    }
    public record ProgressInput(@NotNull @Min(0) Integer progressSeconds) {}
    public record Progress(long lessonId, long progressSeconds, long durationSeconds, boolean completed, String lastLearnTime) {}
    public record Start(long lessonId, long trainingId, int videoDuration, String playUrl, Instant playUrlExpireAt,
                        long resumePositionSeconds, boolean completed) {}
    public record Summary(int totalLessons, long completedLessons, double progressRatio) {}

    @PostMapping("/lessons/{lessonId}/start")
    public Result<Start> start(@PathVariable("lessonId") Long lessonId) {
        long userId = userId(); RemoteClients.Lesson lesson = authorizedLesson(userId, lessonId); requireVideo(lesson);
        Map<Object, Object> state = redisCall(userId, lesson, () -> progress.read(userId, lesson, traceId()));
        RemoteClients.PlayUrl url = required(training.playUrl(lessonId), "视频播放地址不可用");
        log.info("learning started, traceId={}, userId={}, trainingId={}, lessonId={}", traceId(), userId, lesson.trainingId(), lessonId);
        return Result.ok(new Start(lessonId, lesson.trainingId(), lesson.videoDuration(), url.playUrl(), url.expireAt(),
                longValue(state, "progressSeconds"), longValue(state, "completed") == 1));
    }

    @PostMapping("/lessons/{lessonId}/progress")
    public Result<Progress> update(@PathVariable("lessonId") Long lessonId, @Valid @RequestBody ProgressInput input) {
        long userId = userId(); RemoteClients.Lesson lesson = authorizedLesson(userId, lessonId); requireVideo(lesson);
        if (input.progressSeconds() > lesson.videoDuration()) throw new LearningFailure(400, "学习进度超过视频时长");
        long result = redisCall(userId, lesson, () -> progress.update(userId, lesson, input.progressSeconds(), ratio, traceId()));
        if (result == 0) log.debug("old learning progress ignored, traceId={}, userId={}, lessonId={}", traceId(), userId, lessonId);
        if (result == 2) log.info("lesson first completed in Redis, traceId={}, userId={}, trainingId={}, lessonId={}", traceId(), userId, lesson.trainingId(), lessonId);
        return Result.ok(view(lessonId, redisCall(userId, lesson, () -> progress.read(userId, lesson, traceId()))));
    }

    @GetMapping("/lessons/{lessonId}/progress")
    public Result<Progress> get(@PathVariable("lessonId") Long lessonId) {
        long userId = userId(); RemoteClients.Lesson lesson = authorizedLesson(userId, lessonId);
        try { return Result.ok(view(lessonId, progress.peek(userId, lesson, traceId()))); }
        catch (DataAccessException e) {
            log.warn("Redis progress unavailable; reading MySQL, traceId={}, userId={}, trainingId={}, lessonId={}", traceId(), userId, lesson.trainingId(), lessonId, e);
            Map<String, Object> row = progress.persisted(userId, lessonId);
            if (row.isEmpty()) return Result.ok(new Progress(lessonId, 0, lesson.videoDuration() == null ? 0 : lesson.videoDuration(), false, null));
            Object flag = row.get("completed");
            boolean done = flag instanceof Boolean b ? b : ((Number) flag).intValue() == 1;
            return Result.ok(new Progress(lessonId, ((Number) row.get("progress_seconds")).longValue(),
                    ((Number) row.get("duration_seconds")).longValue(), done, String.valueOf(row.get("last_learn_time"))));
        }
    }

    @PostMapping("/lessons/{lessonId}/complete")
    public Result<Void> complete(@PathVariable("lessonId") Long lessonId) {
        long userId = userId(); RemoteClients.Lesson lesson = authorizedLesson(userId, lessonId);
        if (!Integer.valueOf(1).equals(lesson.lessonType())) throw new LearningFailure(400, "仅图文课时可显式完成");
        long result = redisCall(userId, lesson, () -> progress.complete(userId, lesson, traceId()));
        if (result == 1) log.info("text lesson first completed, traceId={}, userId={}, trainingId={}, lessonId={}", traceId(), userId, lesson.trainingId(), lessonId);
        return Result.ok(null);
    }

    @GetMapping("/trainings/{trainingId}/progress")
    public Result<Summary> trainingProgress(@PathVariable("trainingId") Long trainingId) {
        long userId = userId(); requireEnrollment(userId, trainingId);
        return Result.ok(summary(userId, trainingId, null));
    }
    @GetMapping("/chapters/{chapterId}/progress")
    public Result<Summary> chapterProgress(@PathVariable("chapterId") Long chapterId) {
        long userId = userId(); Long trainingId = required(training.chapterTraining(chapterId), "章节不存在");
        requireEnrollment(userId, trainingId); return Result.ok(summary(userId, trainingId, chapterId));
    }
    private Summary summary(long userId, Long trainingId, Long chapterId) {
        RemoteClients.Catalog catalog = required(training.catalog(trainingId), "培训目录不可用");
        List<Long> ids = new ArrayList<>();
        for (RemoteClients.Course course : catalog.courses()) for (RemoteClients.Chapter chapter : course.chapters()) {
            if (chapterId == null || chapterId.equals(chapter.id())) for (RemoteClients.LessonNode lesson : chapter.lessons()) ids.add(lesson.id());
        }
        long done = progress.countCompleted(userId, ids);
        return new Summary(ids.size(), done, ids.isEmpty() ? 0 : (double) done / ids.size());
    }
    private RemoteClients.Lesson authorizedLesson(long userId, Long lessonId) {
        RemoteClients.Lesson lesson = required(training.lesson(lessonId), "课时不存在");
        requireEnrollment(userId, lesson.trainingId()); return lesson;
    }
    private void requireEnrollment(long userId, Long trainingId) {
        if (!Boolean.TRUE.equals(required(enrollment.active(trainingId, userId), "报名校验失败"))) throw new LearningFailure(403, "没有有效报名");
    }
    private static void requireVideo(RemoteClients.Lesson lesson) {
        if (!Integer.valueOf(0).equals(lesson.lessonType())) throw new LearningFailure(400, "非视频课时不能播放视频");
        if (!lesson.videoBound()) throw new LearningFailure(400, "课时未绑定视频");
        if (lesson.videoDuration() == null || lesson.videoDuration() <= 0) throw new LearningFailure(400, "视频时长无效");
    }
    private static <T> T required(RemoteClients.Result<T> result, String message) {
        if (result == null || result.code() == null || result.code() >= 500) throw new LearningFailure(503, message);
        if (result.code() != 200 || result.data() == null) throw new LearningFailure(400, result.message() == null ? message : result.message());
        return result.data();
    }
    private static long userId() {
        RequestIdentity.Identity i = RequestIdentity.current();
        if (i == null || i.userId() == null) throw new LearningFailure(401, "缺少登录身份");
        if (!"STUDENT".equals(i.role())) throw new LearningFailure(403, "仅学生可学习");
        return i.userId();
    }
    private static String traceId() { RequestIdentity.Identity i = RequestIdentity.current(); return i == null ? null : i.traceId(); }
    private static <T> T redisCall(long userId, RemoteClients.Lesson lesson, Supplier<T> action) {
        try { return action.get(); }
        catch (DataAccessException e) {
            log.error("learning progress storage unavailable, traceId={}, userId={}, trainingId={}, lessonId={}",
                    traceId(), userId, lesson.trainingId(), lesson.lessonId(), e);
            throw e;
        }
    }
    private static long longValue(Map<Object, Object> map, String key) { return Long.parseLong(String.valueOf(map.get(key))); }
    private static Progress view(long lessonId, Map<Object, Object> map) {
        return new Progress(lessonId, longValue(map, "progressSeconds"), longValue(map, "durationSeconds"),
                longValue(map, "completed") == 1, String.valueOf(map.get("updatedAt")));
    }
}

class LearningFailure extends RuntimeException {
    final int code;
    LearningFailure(int code, String message) { super(message); this.code = code; }
}

@Slf4j
@RestControllerAdvice
class LearningExceptionHandler {
    @ExceptionHandler(LearningFailure.class)
    ResponseEntity<LearningController.Result<Void>> business(LearningFailure e) {
        return ResponseEntity.status(e.code).body(new LearningController.Result<>(e.code, e.getMessage(), null));
    }
    @ExceptionHandler({FeignException.class, DataAccessException.class})
    ResponseEntity<LearningController.Result<Void>> unavailable(Exception e) {
        log.error("learning dependency unavailable, identity={}", RequestIdentity.current(), e);
        return ResponseEntity.status(503).body(new LearningController.Result<>(503, "学习服务依赖暂时不可用", null));
    }
}
