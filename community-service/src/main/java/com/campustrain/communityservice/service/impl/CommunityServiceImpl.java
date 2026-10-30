package com.campustrain.communityservice.service.impl;

import com.campustrain.communityservice.client.EnrollmentClient;
import com.campustrain.communityservice.client.TrainingClient;
import com.campustrain.communityservice.client.dto.LessonInfo;
import com.campustrain.communityservice.client.dto.Catalog;
import com.campustrain.communityservice.client.dto.RemoteResult;
import com.campustrain.communityservice.context.UserContext;
import com.campustrain.communityservice.context.UserIdentity;
import com.campustrain.communityservice.dto.AnswerRequest;
import com.campustrain.communityservice.dto.CommentRequest;
import com.campustrain.communityservice.dto.CommunityActionEvent;
import com.campustrain.communityservice.dto.QuestionRequest;
import com.campustrain.communityservice.entity.Answer;
import com.campustrain.communityservice.entity.Comment;
import com.campustrain.communityservice.entity.Question;
import com.campustrain.communityservice.exception.CommunityException;
import com.campustrain.communityservice.mapper.CommunityMapper;
import com.campustrain.communityservice.service.CommunityService;
import com.campustrain.communityservice.service.LikeService;
import com.campustrain.communityservice.vo.AnswerView;
import com.campustrain.communityservice.vo.CommentView;
import com.campustrain.communityservice.vo.PageView;
import com.campustrain.communityservice.vo.QuestionView;
import feign.FeignException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.campustrain.communityservice.vo.LikeView;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommunityServiceImpl implements CommunityService {
    private final CommunityMapper mapper;
    private final TrainingClient training;
    private final EnrollmentClient enrollment;
    private final LikeService likes;
    private final ObjectMapper json;

    @Override
    @Transactional
    public long createQuestion(QuestionRequest request) {
        UserIdentity identity = identity();
        if (request.trainingId() == null || request.trainingId() <= 0) throw new CommunityException(400, "培训ID不合法");
        remoteTraining(request.trainingId());
        if (request.lessonId() != null) {
            RemoteResult<LessonInfo> lesson;
            try { lesson = training.lesson(request.lessonId()); }
            catch (FeignException ex) { throw remoteFailure(ex); }
            if (lesson == null || lesson.code() == null || lesson.code() == 503)
                throw new CommunityException(503, "培训服务暂时不可用");
            if (lesson == null || lesson.code() == null || lesson.code() != 200 || lesson.data() == null
                    || !request.trainingId().equals(lesson.data().trainingId())
                    || request.courseId() != null && !request.courseId().equals(lesson.data().courseId())) {
                throw new CommunityException(400, "课时与培训或课程不匹配");
            }
        } else if (request.courseId() != null) {
            RemoteResult<Catalog> catalog;
            try { catalog = training.catalog(request.trainingId()); }
            catch (FeignException ex) { throw remoteFailure(ex); }
            if (catalog == null || catalog.code() == null || catalog.code() != 200 || catalog.data() == null
                    || catalog.data().courses() == null || catalog.data().courses().stream()
                    .noneMatch(course -> request.courseId().equals(course.id()))) {
                throw new CommunityException(400, "课程与培训不匹配");
            }
        }
        if ("STUDENT".equals(identity.role())) {
            RemoteResult<Boolean> active;
            try { active = enrollment.active(request.trainingId(), identity.userId()); }
            catch (FeignException ex) { throw remoteFailure(ex); }
            if (active == null || active.code() == null || active.code() == 503)
                throw new CommunityException(503, "报名服务暂时不可用");
            if (active.code() != 200 || !Boolean.TRUE.equals(active.data()))
                throw new CommunityException(403, "学生需要有效报名才能提问");
        }
        long id = mapper.insertQuestion(identity.userId(), request);
        writeEvent("QUESTION_CREATED", "QUESTION", id, identity);
        log.info("question created, traceId={}, userId={}, targetId={}", identity.traceId(), identity.userId(), id);
        return id;
    }

    @Override
    public PageView<QuestionView> questions(Long trainingId, Long lessonId, int page, int size) {
        paging(page, size);
        List<QuestionView> rows = mapper.questions(trainingId, lessonId, page, size).stream()
                .map(row -> questionView(row, false)).toList();
        return new PageView<>(mapper.questionCount(trainingId, lessonId), page, size, rows);
    }

    @Override
    public QuestionView question(long id) {
        Question row = mapper.question(id);
        if (row == null) throw new CommunityException(404, "问题不存在");
        List<AnswerView> answers = answerViews(mapper.answers(id, 1, 100));
        List<CommentView> comments = mapper.comments("QUESTION", id, 1, 100).stream().map(this::commentView).toList();
        QuestionView base = questionView(row, true);
        return new QuestionView(base.id(), base.trainingId(), base.courseId(), base.lessonId(), base.userId(),
                base.title(), base.content(), base.answerCount(), base.likeCount(), base.likedByMe(),
                base.createTime(), answers, comments);
    }

    @Override
    @Transactional
    public long createAnswer(long questionId, AnswerRequest request) {
        UserIdentity identity = identity();
        if (mapper.question(questionId) == null) throw new CommunityException(404, "问题不存在");
        long id = mapper.insertAnswer(questionId, identity.userId(), request.content());
        mapper.incrementAnswerCount(questionId);
        writeEvent("ANSWER_CREATED", "ANSWER", id, identity);
        log.info("answer created, traceId={}, userId={}, targetId={}", identity.traceId(), identity.userId(), id);
        return id;
    }

    @Override
    public PageView<AnswerView> answers(long questionId, int page, int size) {
        paging(page, size);
        if (mapper.question(questionId) == null) throw new CommunityException(404, "问题不存在");
        return new PageView<>(mapper.answerCount(questionId), page, size,
                answerViews(mapper.answers(questionId, page, size)));
    }

    @Override
    @Transactional
    public long createComment(CommentRequest request) {
        UserIdentity identity = identity();
        String type = LikeService.targetType(request.targetType());
        if (!targetExists(type, request.targetId())) throw new CommunityException(404, "评论目标不存在");
        long id = mapper.insertComment(type, request.targetId(), identity.userId(), request.content());
        mapper.incrementCommentCount(type, request.targetId());
        log.info("comment created, traceId={}, userId={}, targetType={}, targetId={}",
                identity.traceId(), identity.userId(), type, request.targetId());
        return id;
    }

    @Override
    public PageView<CommentView> comments(String type, long targetId, int page, int size) {
        paging(page, size);
        type = LikeService.targetType(type);
        if (!targetExists(type, targetId)) throw new CommunityException(404, "评论目标不存在");
        return new PageView<>(mapper.commentCount(type, targetId), page, size,
                mapper.comments(type, targetId, page, size).stream().map(this::commentView).toList());
    }

    private QuestionView questionView(Question row, boolean includeLiveLike) {
        long count = row.likeCount();
        boolean liked = false;
        if (includeLiveLike) {
            var state = likes.read("QUESTION", row.id(), row.likeCount());
            count = state.likeCount(); liked = state.likedByMe();
        }
        return new QuestionView(row.id(), row.trainingId(), row.courseId(), row.lessonId(), row.userId(),
                row.title(), row.content(), row.answerCount(), count, liked, row.createTime(), List.of(), List.of());
    }

    private List<AnswerView> answerViews(List<Answer> rows) {
        if (rows.isEmpty()) return List.of();
        Map<Long, LikeView> states = likes.readBatch("ANSWER", rows.stream().collect(
                Collectors.toMap(Answer::id, Answer::likeCount)));
        return rows.stream().map(row -> {
            LikeView state = states.get(row.id());
            return new AnswerView(row.id(), row.questionId(), row.userId(), row.content(), row.commentCount(),
                    state.likeCount(), state.likedByMe(), row.createTime(), List.of());
        }).toList();
    }

    private CommentView commentView(Comment row) {
        return new CommentView(row.id(), row.targetType(), row.targetId(), row.userId(), row.content(), row.createTime());
    }

    private boolean targetExists(String type, long id) {
        return "QUESTION".equals(type) ? mapper.question(id) != null : mapper.answer(id) != null;
    }

    private void remoteTraining(long id) {
        RemoteResult<Object> result;
        try { result = training.training(id); }
        catch (FeignException ex) { throw remoteFailure(ex); }
        if (result == null || result.code() == null || result.code() != 200 || result.data() == null)
            throw new CommunityException(result != null && result.code() != null && result.code() == 503 ? 503 : 404,
                    "培训不存在或暂不可用");
    }

    private CommunityException remoteFailure(FeignException ex) {
        log.error("community Feign unavailable, traceId={}", UserContext.traceId(), ex);
        return new CommunityException(503, "依赖服务暂时不可用");
    }

    private UserIdentity identity() {
        UserIdentity identity = UserContext.current();
        if (identity == null || identity.userId() == null) throw new CommunityException(401, "用户身份缺失");
        return identity;
    }

    private void writeEvent(String action, String type, long targetId, UserIdentity identity) {
        CommunityActionEvent event = new CommunityActionEvent(UUID.randomUUID().toString(), "COMMUNITY_ACTION",
                identity.userId(), targetId, type, action, LocalDateTime.now().toString(), identity.traceId());
        mapper.insertOutbox(event.eventId(), event.eventType(), targetId, json.writeValueAsString(event));
    }

    private void paging(int page, int size) {
        if (page < 1 || size < 1 || size > 100) throw new CommunityException(400, "分页参数不合法");
    }
}
