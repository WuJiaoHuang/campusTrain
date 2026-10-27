package com.campustrain.learningservice.service;

import com.campustrain.learningservice.client.EnrollmentClient;
import com.campustrain.learningservice.client.TrainingClient;
import com.campustrain.learningservice.client.dto.Catalog;
import com.campustrain.learningservice.client.dto.Chapter;
import com.campustrain.learningservice.client.dto.Course;
import com.campustrain.learningservice.client.dto.Lesson;
import com.campustrain.learningservice.client.dto.LessonNode;
import com.campustrain.learningservice.client.dto.PlayUrl;
import com.campustrain.learningservice.client.dto.RemoteResult;
import com.campustrain.learningservice.context.RequestIdentityContext;
import com.campustrain.learningservice.context.UserIdentity;
import com.campustrain.learningservice.exception.LearningFailure;
import com.campustrain.learningservice.vo.LearningProgressVO;
import com.campustrain.learningservice.vo.LearningStartVO;
import com.campustrain.learningservice.vo.LearningSummaryVO;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class LearningService {
    private final TrainingClient training;
    private final EnrollmentClient enrollment;
    private final LearningProgressStore progress;
    private final double ratio;

    public LearningService(TrainingClient training, EnrollmentClient enrollment, LearningProgressStore progress,
                           @Value("${campustrain.learning.complete-ratio:0.90}") double ratio) {
        if (ratio <= 0 || ratio > 1) {
            throw new IllegalArgumentException("invalid learning complete ratio");
        }
        this.training = training;
        this.enrollment = enrollment;
        this.progress = progress;
        this.ratio = ratio;
    }

    public LearningStartVO start(Long lessonId) {
        long userId = userId();
        Lesson lesson = authorizedLesson(userId, lessonId);
        requireVideo(lesson);
        Map<Object, Object> state = redisCall(userId, lesson, () -> progress.read(userId, lesson, traceId()));
        PlayUrl url = required(training.playUrl(lessonId), "视频播放地址不可用");
        log.info("learning started, traceId={}, userId={}, trainingId={}, lessonId={}", traceId(), userId,
                lesson.trainingId(), lessonId);
        return new LearningStartVO(lessonId, lesson.trainingId(), lesson.videoDuration(), url.playUrl(), url.expireAt(),
                longValue(state, "progressSeconds"), longValue(state, "completed") == 1);
    }

    public LearningProgressVO update(Long lessonId, Integer progressSeconds) {
        long userId = userId();
        Lesson lesson = authorizedLesson(userId, lessonId);
        requireVideo(lesson);
        if (progressSeconds > lesson.videoDuration()) {
            throw new LearningFailure(400, "学习进度超过视频时长");
        }
        long result = redisCall(userId, lesson, () -> progress.update(userId, lesson, progressSeconds, ratio, traceId()));
        if (result == 0) {
            log.debug("old learning progress ignored, traceId={}, userId={}, lessonId={}", traceId(), userId, lessonId);
        }
        if (result == 2) {
            log.info("lesson first completed in Redis, traceId={}, userId={}, trainingId={}, lessonId={}", traceId(),
                    userId, lesson.trainingId(), lessonId);
        }
        return view(lessonId, redisCall(userId, lesson, () -> progress.read(userId, lesson, traceId())));
    }

    public LearningProgressVO getProgress(Long lessonId) {
        long userId = userId();
        Lesson lesson = authorizedLesson(userId, lessonId);
        try {
            return view(lessonId, progress.peek(userId, lesson, traceId()));
        } catch (DataAccessException exception) {
            log.warn("Redis progress unavailable; reading MySQL, traceId={}, userId={}, trainingId={}, lessonId={}",
                    traceId(), userId, lesson.trainingId(), lessonId, exception);
            Map<String, Object> row = progress.persisted(userId, lessonId);
            if (row.isEmpty()) {
                return new LearningProgressVO(lessonId, 0, lesson.videoDuration() == null ? 0 : lesson.videoDuration(),
                        false, null);
            }
            Object flag = row.get("completed");
            boolean done = flag instanceof Boolean value ? value : ((Number) flag).intValue() == 1;
            return new LearningProgressVO(lessonId, ((Number) row.get("progress_seconds")).longValue(),
                    ((Number) row.get("duration_seconds")).longValue(), done, String.valueOf(row.get("last_learn_time")));
        }
    }

    public void complete(Long lessonId) {
        long userId = userId();
        Lesson lesson = authorizedLesson(userId, lessonId);
        if (!Integer.valueOf(1).equals(lesson.lessonType())) {
            throw new LearningFailure(400, "仅图文课时可显式完成");
        }
        long result = redisCall(userId, lesson, () -> progress.complete(userId, lesson, traceId()));
        if (result == 1) {
            log.info("text lesson first completed, traceId={}, userId={}, trainingId={}, lessonId={}", traceId(), userId,
                    lesson.trainingId(), lessonId);
        }
    }

    public LearningSummaryVO trainingProgress(Long trainingId) {
        long userId = userId();
        requireEnrollment(userId, trainingId);
        return summary(userId, trainingId, null);
    }

    public LearningSummaryVO chapterProgress(Long chapterId) {
        long userId = userId();
        Long trainingId = required(training.chapterTraining(chapterId), "章节不存在");
        requireEnrollment(userId, trainingId);
        return summary(userId, trainingId, chapterId);
    }

    private LearningSummaryVO summary(long userId, Long trainingId, Long chapterId) {
        Catalog catalog = required(training.catalog(trainingId), "培训目录不可用");
        List<Long> ids = new ArrayList<>();
        for (Course course : catalog.courses()) {
            for (Chapter chapter : course.chapters()) {
                if (chapterId == null || chapterId.equals(chapter.id())) {
                    for (LessonNode lesson : chapter.lessons()) {
                        ids.add(lesson.id());
                    }
                }
            }
        }
        long done = progress.countCompleted(userId, ids);
        return new LearningSummaryVO(ids.size(), done, ids.isEmpty() ? 0 : (double) done / ids.size());
    }

    private Lesson authorizedLesson(long userId, Long lessonId) {
        Lesson lesson = required(training.lesson(lessonId), "课时不存在");
        requireEnrollment(userId, lesson.trainingId());
        return lesson;
    }

    private void requireEnrollment(long userId, Long trainingId) {
        if (!Boolean.TRUE.equals(required(enrollment.active(trainingId, userId), "报名校验失败"))) {
            throw new LearningFailure(403, "没有有效报名");
        }
    }

    private static void requireVideo(Lesson lesson) {
        if (!Integer.valueOf(0).equals(lesson.lessonType())) {
            throw new LearningFailure(400, "非视频课时不能播放视频");
        }
        if (!lesson.videoBound()) {
            throw new LearningFailure(400, "课时未绑定视频");
        }
        if (lesson.videoDuration() == null || lesson.videoDuration() <= 0) {
            throw new LearningFailure(400, "视频时长无效");
        }
    }

    private static <T> T required(RemoteResult<T> result, String message) {
        if (result == null || result.code() == null || result.code() >= 500) {
            throw new LearningFailure(503, message);
        }
        if (result.code() != 200 || result.data() == null) {
            throw new LearningFailure(400, result.message() == null ? message : result.message());
        }
        return result.data();
    }

    private static long userId() {
        UserIdentity identity = RequestIdentityContext.current();
        if (identity == null || identity.userId() == null) {
            throw new LearningFailure(401, "缺少登录身份");
        }
        if (!"STUDENT".equals(identity.role())) {
            throw new LearningFailure(403, "仅学生可学习");
        }
        return identity.userId();
    }

    private static String traceId() {
        UserIdentity identity = RequestIdentityContext.current();
        return identity == null ? null : identity.traceId();
    }

    private static <T> T redisCall(long userId, Lesson lesson, Supplier<T> action) {
        try {
            return action.get();
        } catch (DataAccessException exception) {
            log.error("learning progress storage unavailable, traceId={}, userId={}, trainingId={}, lessonId={}",
                    traceId(), userId, lesson.trainingId(), lesson.lessonId(), exception);
            throw exception;
        }
    }

    private static long longValue(Map<Object, Object> map, String key) {
        return Long.parseLong(String.valueOf(map.get(key)));
    }

    private static LearningProgressVO view(long lessonId, Map<Object, Object> map) {
        return new LearningProgressVO(lessonId, longValue(map, "progressSeconds"), longValue(map, "durationSeconds"),
                longValue(map, "completed") == 1, String.valueOf(map.get("updatedAt")));
    }
}
