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
import com.campustrain.communityservice.entity.QuestionInsert;
import com.campustrain.communityservice.entity.AnswerInsert;
import com.campustrain.communityservice.entity.CommentInsert;
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
    private final CommunityMapper communityMapper;
    private final TrainingClient trainingClient;
    private final EnrollmentClient enrollmentClient;
    private final LikeService likeService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public long createQuestion(QuestionRequest request) {
        UserIdentity identity = requireIdentity();
        if (request.trainingId() == null || request.trainingId() <= 0) throw new CommunityException(400, "培训ID不合法");
        validateTrainingExists(request.trainingId());
        if (request.lessonId() != null) {
            RemoteResult<LessonInfo> lesson;
            try { lesson = trainingClient.getLessonInfo(request.lessonId()); }
            catch (FeignException ex) { throw remoteServiceFailure(ex); }
            if (lesson == null || lesson.code() == null || lesson.code() == 503)
                throw new CommunityException(503, "培训服务暂时不可用");
            if (lesson == null || lesson.code() == null || lesson.code() != 200 || lesson.data() == null
                    || !request.trainingId().equals(lesson.data().trainingId())
                    || request.courseId() != null && !request.courseId().equals(lesson.data().courseId())) {
                throw new CommunityException(400, "课时与培训或课程不匹配");
            }
        } else if (request.courseId() != null) {
            RemoteResult<Catalog> catalog;
            try { catalog = trainingClient.getCatalog(request.trainingId()); }
            catch (FeignException ex) { throw remoteServiceFailure(ex); }
            if (catalog == null || catalog.code() == null || catalog.code() != 200 || catalog.data() == null
                    || catalog.data().courses() == null || catalog.data().courses().stream()
                    .noneMatch(course -> request.courseId().equals(course.id()))) {
                throw new CommunityException(400, "课程与培训不匹配");
            }
        }
        if ("STUDENT".equals(identity.role())) {
            RemoteResult<Boolean> active;
            try { active = enrollmentClient.hasActiveEnrollment(request.trainingId(), identity.userId()); }
            catch (FeignException ex) { throw remoteServiceFailure(ex); }
            if (active == null || active.code() == null || active.code() == 503)
                throw new CommunityException(503, "报名服务暂时不可用");
            if (active.code() != 200 || !Boolean.TRUE.equals(active.data()))
                throw new CommunityException(403, "学生需要有效报名才能提问");
        }
        QuestionInsert questionInsert = new QuestionInsert(null, request.trainingId(), request.courseId(),
                request.lessonId(), identity.userId(), request.title().trim(), request.content().trim());
        communityMapper.insertQuestion(questionInsert);
        long id = questionInsert.getId();
        insertCommunityOutboxEvent("QUESTION_CREATED", "QUESTION", id, identity);
        log.info("question created, traceId={}, userId={}, targetId={}", identity.traceId(), identity.userId(), id);
        return id;
    }

    @Override
    public PageView<QuestionView> getQuestionPage(Long trainingId, Long lessonId, int page, int size) {
        validatePagination(page, size);
        List<QuestionView> rows = communityMapper.selectQuestionPage(trainingId, lessonId, (page - 1) * size, size).stream()
                .map(row -> toQuestionView(row, false)).toList();
        return new PageView<>(communityMapper.countQuestions(trainingId, lessonId), page, size, rows);
    }

    @Override
    public QuestionView getQuestionDetail(long id) {
        Question row = communityMapper.selectQuestionById(id);
        if (row == null) throw new CommunityException(404, "问题不存在");
        List<AnswerView> answers = toAnswerViews(communityMapper.selectAnswerPage(id, 0, 100));
        List<CommentView> comments = communityMapper.selectCommentPage("QUESTION", id, 0, 100).stream().map(this::toCommentView).toList();
        QuestionView base = toQuestionView(row, true);
        return new QuestionView(base.id(), base.trainingId(), base.courseId(), base.lessonId(), base.userId(),
                base.title(), base.content(), base.answerCount(), base.likeCount(), base.likedByMe(),
                base.createTime(), answers, comments);
    }

    @Override
    @Transactional
    public long createAnswer(long questionId, AnswerRequest request) {
        UserIdentity identity = requireIdentity();
        if (communityMapper.selectQuestionById(questionId) == null) throw new CommunityException(404, "问题不存在");
        AnswerInsert answerInsert = new AnswerInsert(null, questionId, identity.userId(), request.content().trim());
        communityMapper.insertAnswer(answerInsert);
        long id = answerInsert.getId();
        if (communityMapper.incrementQuestionAnswerCount(questionId) != 1)
            throw new IllegalStateException("question count update failed");
        insertCommunityOutboxEvent("ANSWER_CREATED", "ANSWER", id, identity);
        log.info("answer created, traceId={}, userId={}, targetId={}", identity.traceId(), identity.userId(), id);
        return id;
    }

    @Override
    public PageView<AnswerView> getAnswerPage(long questionId, int page, int size) {
        validatePagination(page, size);
        if (communityMapper.selectQuestionById(questionId) == null) throw new CommunityException(404, "问题不存在");
        return new PageView<>(communityMapper.countAnswers(questionId), page, size,
                toAnswerViews(communityMapper.selectAnswerPage(questionId, (page - 1) * size, size)));
    }

    @Override
    @Transactional
    public long createComment(CommentRequest request) {
        UserIdentity identity = requireIdentity();
        String type = LikeService.targetType(request.targetType());
        if (!doesTargetExist(type, request.targetId())) throw new CommunityException(404, "评论目标不存在");
        CommentInsert commentInsert = new CommentInsert(null, type, request.targetId(), identity.userId(), request.content().trim());
        communityMapper.insertComment(commentInsert);
        long id = commentInsert.getId();
        int updated = "QUESTION".equals(type)
                ? communityMapper.incrementQuestionCommentCount(request.targetId())
                : communityMapper.incrementAnswerCommentCount(request.targetId());
        if (updated != 1) throw new IllegalStateException("comment count update failed");
        log.info("comment created, traceId={}, userId={}, targetType={}, targetId={}",
                identity.traceId(), identity.userId(), type, request.targetId());
        return id;
    }

    @Override
    public PageView<CommentView> getCommentPage(String type, long targetId, int page, int size) {
        validatePagination(page, size);
        type = LikeService.targetType(type);
        if (!doesTargetExist(type, targetId)) throw new CommunityException(404, "评论目标不存在");
        return new PageView<>(communityMapper.countComments(type, targetId), page, size,
                communityMapper.selectCommentPage(type, targetId, (page - 1) * size, size).stream().map(this::toCommentView).toList());
    }

    private QuestionView toQuestionView(Question row, boolean includeLiveLike) {
        long count = row.likeCount();
        boolean liked = false;
        if (includeLiveLike) {
            var state = likeService.getLikeState("QUESTION", row.id(), row.likeCount());
            count = state.likeCount(); liked = state.likedByMe();
        }
        return new QuestionView(row.id(), row.trainingId(), row.courseId(), row.lessonId(), row.userId(),
                row.title(), row.content(), row.answerCount(), count, liked, row.createTime(), List.of(), List.of());
    }

    private List<AnswerView> toAnswerViews(List<Answer> rows) {
        if (rows.isEmpty()) return List.of();
        Map<Long, LikeView> states = likeService.getLikeStates("ANSWER", rows.stream().collect(
                Collectors.toMap(Answer::id, Answer::likeCount)));
        return rows.stream().map(row -> {
            LikeView state = states.get(row.id());
            return new AnswerView(row.id(), row.questionId(), row.userId(), row.content(), row.commentCount(),
                    state.likeCount(), state.likedByMe(), row.createTime(), List.of());
        }).toList();
    }

    private CommentView toCommentView(Comment row) {
        return new CommentView(row.id(), row.targetType(), row.targetId(), row.userId(), row.content(), row.createTime());
    }

    private boolean doesTargetExist(String type, long id) {
        return "QUESTION".equals(type) ? communityMapper.selectQuestionById(id) != null : communityMapper.selectAnswerById(id) != null;
    }

    private void validateTrainingExists(long id) {
        RemoteResult<Object> result;
        try { result = trainingClient.getTrainingEnrollmentCheck(id); }
        catch (FeignException ex) { throw remoteServiceFailure(ex); }
        if (result == null || result.code() == null || result.code() != 200 || result.data() == null)
            throw new CommunityException(result != null && result.code() != null && result.code() == 503 ? 503 : 404,
                    "培训不存在或暂不可用");
    }

    private CommunityException remoteServiceFailure(FeignException ex) {
        log.error("community Feign unavailable, traceId={}", UserContext.traceId(), ex);
        return new CommunityException(503, "依赖服务暂时不可用");
    }

    private UserIdentity requireIdentity() {
        UserIdentity identity = UserContext.current();
        if (identity == null || identity.userId() == null) throw new CommunityException(401, "用户身份缺失");
        return identity;
    }

    private void insertCommunityOutboxEvent(String action, String type, long targetId, UserIdentity identity) {
        CommunityActionEvent event = new CommunityActionEvent(UUID.randomUUID().toString(), "COMMUNITY_ACTION",
                identity.userId(), targetId, type, action, LocalDateTime.now().toString(), identity.traceId());
        communityMapper.insertOutboxEvent(event.eventId(), event.eventType(), targetId, objectMapper.writeValueAsString(event));
    }

    private void validatePagination(int page, int size) {
        if (page < 1 || size < 1 || size > 100) throw new CommunityException(400, "分页参数不合法");
    }
}
