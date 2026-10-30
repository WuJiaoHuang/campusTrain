package com.campustrain.communityservice.mapper;

import com.campustrain.communityservice.dto.QuestionRequest;
import com.campustrain.communityservice.entity.Answer;
import com.campustrain.communityservice.entity.Comment;
import com.campustrain.communityservice.entity.Question;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class CommunityMapper {
    private final JdbcTemplate jdbc;
    private final RowMapper<Question> questionRow = (rs, row) -> new Question(rs.getLong("id"),
            rs.getLong("training_id"), rs.getObject("course_id", Long.class),
            rs.getObject("lesson_id", Long.class), rs.getLong("user_id"),
            rs.getString("title"), rs.getString("content"), rs.getInt("answer_count"),
            rs.getLong("like_count"), rs.getTimestamp("create_time").toLocalDateTime());
    private final RowMapper<Answer> answerRow = (rs, row) -> new Answer(rs.getLong("id"),
            rs.getLong("question_id"), rs.getLong("user_id"), rs.getString("content"),
            rs.getInt("comment_count"), rs.getLong("like_count"), rs.getTimestamp("create_time").toLocalDateTime());
    private final RowMapper<Comment> commentRow = (rs, row) -> new Comment(rs.getLong("id"),
            rs.getString("target_type"), rs.getLong("target_id"), rs.getLong("user_id"),
            rs.getString("content"), rs.getTimestamp("create_time").toLocalDateTime());

    public CommunityMapper(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public long insertQuestion(long userId, QuestionRequest request) {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO question(training_id,course_id,lesson_id,user_id,title,content,answer_count,comment_count,like_count,status,create_time,update_time) VALUES(?,?,?,?,?,?,0,0,0,1,NOW(),NOW())",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, request.trainingId());
            statement.setObject(2, request.courseId());
            statement.setObject(3, request.lessonId());
            statement.setLong(4, userId);
            statement.setString(5, request.title().trim());
            statement.setString(6, request.content().trim());
            return statement;
        }, key);
        return key.getKey().longValue();
    }

    public long insertAnswer(long questionId, long userId, String content) {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO answer(question_id,user_id,content,comment_count,like_count,status,create_time,update_time) VALUES(?,?,?,0,0,1,NOW(),NOW())",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, questionId);
            statement.setLong(2, userId);
            statement.setString(3, content.trim());
            return statement;
        }, key);
        return key.getKey().longValue();
    }

    public long insertComment(String type, long targetId, long userId, String content) {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO comment(target_type,target_id,user_id,content,status,create_time,update_time) VALUES(?,?,?,?,1,NOW(),NOW())",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, type); statement.setLong(2, targetId);
            statement.setLong(3, userId); statement.setString(4, content.trim());
            return statement;
        }, key);
        return key.getKey().longValue();
    }

    public Question question(long id) {
        List<Question> rows = jdbc.query("SELECT * FROM question WHERE id=? AND status=1", questionRow, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public Answer answer(long id) {
        List<Answer> rows = jdbc.query("SELECT * FROM answer WHERE id=? AND status=1", answerRow, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<Question> questions(Long trainingId, Long lessonId, int page, int size) {
        StringBuilder sql = new StringBuilder("SELECT * FROM question WHERE status=1");
        List<Object> args = filter(sql, trainingId, lessonId);
        sql.append(" ORDER BY id DESC LIMIT ? OFFSET ?");
        args.add(size); args.add((page - 1) * size);
        return jdbc.query(sql.toString(), questionRow, args.toArray());
    }

    public long questionCount(Long trainingId, Long lessonId) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM question WHERE status=1");
        List<Object> args = filter(sql, trainingId, lessonId);
        return jdbc.queryForObject(sql.toString(), Long.class, args.toArray());
    }

    private List<Object> filter(StringBuilder sql, Long trainingId, Long lessonId) {
        List<Object> args = new ArrayList<>();
        if (trainingId != null) { sql.append(" AND training_id=?"); args.add(trainingId); }
        if (lessonId != null) { sql.append(" AND lesson_id=?"); args.add(lessonId); }
        return args;
    }

    public List<Answer> answers(long questionId, int page, int size) {
        return jdbc.query("SELECT * FROM answer WHERE question_id=? AND status=1 ORDER BY id ASC LIMIT ? OFFSET ?",
                answerRow, questionId, size, (page - 1) * size);
    }

    public long answerCount(long questionId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM answer WHERE question_id=? AND status=1", Long.class, questionId);
    }

    public List<Comment> comments(String type, long targetId, int page, int size) {
        return jdbc.query("SELECT * FROM comment WHERE target_type=? AND target_id=? AND status=1 ORDER BY id ASC LIMIT ? OFFSET ?",
                commentRow, type, targetId, size, (page - 1) * size);
    }

    public long commentCount(String type, long targetId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM comment WHERE target_type=? AND target_id=? AND status=1",
                Long.class, type, targetId);
    }

    public void incrementAnswerCount(long questionId) {
        if (jdbc.update("UPDATE question SET answer_count=answer_count+1,update_time=NOW() WHERE id=? AND status=1",
                questionId) != 1) throw new IllegalStateException("question count update failed");
    }

    public void incrementCommentCount(String type, long targetId) {
        String table = "QUESTION".equals(type) ? "question" : "answer";
        if (jdbc.update("UPDATE " + table + " SET comment_count=comment_count+1,update_time=NOW() WHERE id=? AND status=1",
                targetId) != 1) throw new IllegalStateException("comment count update failed");
    }

    public Set<String> activeLikers(String type, long targetId) {
        return new HashSet<>(jdbc.queryForList(
                "SELECT CAST(user_id AS CHAR) FROM like_relation WHERE target_type=? AND target_id=? AND status=1",
                String.class, type, targetId));
    }

    public Set<Long> likedTargets(String type, Set<Long> targets, long userId) {
        if (targets.isEmpty()) return Set.of();
        String placeholders = String.join(",", java.util.Collections.nCopies(targets.size(), "?"));
        List<Object> args = new ArrayList<>();
        args.add(type); args.add(userId); args.addAll(targets);
        return new HashSet<>(jdbc.queryForList(
                "SELECT target_id FROM like_relation WHERE target_type=? AND user_id=? AND status=1 AND target_id IN (" + placeholders + ")",
                Long.class, args.toArray()));
    }

    public void synchronizeLikes(String type, long targetId, Set<String> desired) {
        Set<String> current = activeLikers(type, targetId);
        for (String user : desired) {
            if (!current.contains(user)) jdbc.update(
                    "INSERT INTO like_relation(user_id,target_type,target_id,status,create_time,update_time) VALUES(?,?,?,1,NOW(),NOW()) ON DUPLICATE KEY UPDATE status=1,update_time=NOW()",
                    Long.parseLong(user), type, targetId);
        }
        for (String user : current) {
            if (!desired.contains(user)) jdbc.update(
                    "UPDATE like_relation SET status=0,update_time=NOW() WHERE user_id=? AND target_type=? AND target_id=? AND status=1",
                    Long.parseLong(user), type, targetId);
        }
        String table = "QUESTION".equals(type) ? "question" : "answer";
        jdbc.update("UPDATE " + table + " SET like_count=?,update_time=NOW() WHERE id=? AND status=1",
                desired.size(), targetId);
    }

    public void insertOutbox(String eventId, String type, long targetId, String payload) {
        jdbc.update("INSERT INTO event_outbox(event_id,event_type,target_id,payload,status,retry_count,create_time,update_time) VALUES(?,?,?,?,'NEW',0,NOW(),NOW())",
                eventId, type, targetId, payload);
    }
}
