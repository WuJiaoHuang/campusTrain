package com.campustrain.communityservice.mapper;

import com.campustrain.communityservice.entity.Answer;
import com.campustrain.communityservice.entity.AnswerInsert;
import com.campustrain.communityservice.entity.Comment;
import com.campustrain.communityservice.entity.CommentInsert;
import com.campustrain.communityservice.entity.EventOutbox;
import com.campustrain.communityservice.entity.Question;
import com.campustrain.communityservice.entity.QuestionInsert;
import java.util.List;
import java.util.Set;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CommunityMapper {
    String QUESTION_COLUMNS = "id,training_id,course_id,lesson_id,user_id,title,content,answer_count,like_count,create_time";
    String ANSWER_COLUMNS = "id,question_id,user_id,content,comment_count,like_count,create_time";
    String COMMENT_COLUMNS = "id,target_type,target_id,user_id,content,create_time";

    @Insert("INSERT INTO question(training_id,course_id,lesson_id,user_id,title,content,answer_count,comment_count,like_count,status,create_time,update_time) " +
            "VALUES(#{trainingId},#{courseId},#{lessonId},#{userId},#{title},#{content},0,0,0,1,NOW(),NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertQuestion(QuestionInsert question);

    @Insert("INSERT INTO answer(question_id,user_id,content,comment_count,like_count,status,create_time,update_time) " +
            "VALUES(#{questionId},#{userId},#{content},0,0,1,NOW(),NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertAnswer(AnswerInsert answer);

    @Insert("INSERT INTO comment(target_type,target_id,user_id,content,status,create_time,update_time) " +
            "VALUES(#{targetType},#{targetId},#{userId},#{content},1,NOW(),NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertComment(CommentInsert comment);

    @Select("SELECT " + QUESTION_COLUMNS + " FROM question WHERE id=#{id} AND status=1")
    Question selectQuestionById(@Param("id") long id);

    @Select("SELECT " + ANSWER_COLUMNS + " FROM answer WHERE id=#{id} AND status=1")
    Answer selectAnswerById(@Param("id") long id);

    @Select("<script>SELECT " + QUESTION_COLUMNS + " FROM question WHERE status=1 " +
            "<if test='trainingId != null'>AND training_id=#{trainingId}</if> " +
            "<if test='lessonId != null'>AND lesson_id=#{lessonId}</if> " +
            "ORDER BY id DESC LIMIT #{size} OFFSET #{offset}</script>")
    List<Question> selectQuestionPage(@Param("trainingId") Long trainingId, @Param("lessonId") Long lessonId,
                                      @Param("offset") int offset, @Param("size") int size);

    @Select("<script>SELECT COUNT(*) FROM question WHERE status=1 " +
            "<if test='trainingId != null'>AND training_id=#{trainingId}</if> " +
            "<if test='lessonId != null'>AND lesson_id=#{lessonId}</if></script>")
    long countQuestions(@Param("trainingId") Long trainingId, @Param("lessonId") Long lessonId);

    @Select("SELECT " + ANSWER_COLUMNS + " FROM answer WHERE question_id=#{questionId} AND status=1 " +
            "ORDER BY id ASC LIMIT #{size} OFFSET #{offset}")
    List<Answer> selectAnswerPage(@Param("questionId") long questionId, @Param("offset") int offset,
                                  @Param("size") int size);

    @Select("SELECT COUNT(*) FROM answer WHERE question_id=#{questionId} AND status=1")
    long countAnswers(@Param("questionId") long questionId);

    @Select("SELECT " + COMMENT_COLUMNS + " FROM comment WHERE target_type=#{type} AND target_id=#{targetId} " +
            "AND status=1 ORDER BY id ASC LIMIT #{size} OFFSET #{offset}")
    List<Comment> selectCommentPage(@Param("type") String type, @Param("targetId") long targetId,
                                    @Param("offset") int offset, @Param("size") int size);

    @Select("SELECT COUNT(*) FROM comment WHERE target_type=#{type} AND target_id=#{targetId} AND status=1")
    long countComments(@Param("type") String type, @Param("targetId") long targetId);

    @Update("UPDATE question SET answer_count=answer_count+1,update_time=NOW() WHERE id=#{id} AND status=1")
    int incrementQuestionAnswerCount(@Param("id") long id);

    @Update("UPDATE question SET comment_count=comment_count+1,update_time=NOW() WHERE id=#{id} AND status=1")
    int incrementQuestionCommentCount(@Param("id") long id);

    @Update("UPDATE answer SET comment_count=comment_count+1,update_time=NOW() WHERE id=#{id} AND status=1")
    int incrementAnswerCommentCount(@Param("id") long id);

    @Select("SELECT CAST(user_id AS CHAR) FROM like_relation WHERE target_type=#{type} AND target_id=#{targetId} AND status=1")
    Set<String> selectActiveLikers(@Param("type") String type, @Param("targetId") long targetId);

    @Select("<script>SELECT target_id FROM like_relation WHERE target_type=#{type} AND user_id=#{userId} " +
            "AND status=1 AND target_id IN <foreach collection='targets' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    Set<Long> selectLikedTargetIds(@Param("type") String type, @Param("targets") Set<Long> targets,
                                   @Param("userId") long userId);

    @Insert("INSERT INTO like_relation(user_id,target_type,target_id,status,create_time,update_time) " +
            "VALUES(#{userId},#{type},#{targetId},1,NOW(),NOW()) ON DUPLICATE KEY UPDATE status=1,update_time=NOW()")
    int upsertActiveLike(@Param("userId") long userId, @Param("type") String type, @Param("targetId") long targetId);

    @Update("UPDATE like_relation SET status=0,update_time=NOW() WHERE user_id=#{userId} " +
            "AND target_type=#{type} AND target_id=#{targetId} AND status=1")
    int deactivateLike(@Param("userId") long userId, @Param("type") String type, @Param("targetId") long targetId);

    @Update("UPDATE question SET like_count=#{count},update_time=NOW() WHERE id=#{id} AND status=1")
    int updateQuestionLikeCount(@Param("id") long id, @Param("count") long count);

    @Update("UPDATE answer SET like_count=#{count},update_time=NOW() WHERE id=#{id} AND status=1")
    int updateAnswerLikeCount(@Param("id") long id, @Param("count") long count);

    @Insert("INSERT INTO event_outbox(event_id,event_type,target_id,payload,status,retry_count,create_time,update_time) " +
            "VALUES(#{eventId},#{type},#{targetId},#{payload},'NEW',0,NOW(),NOW())")
    int insertOutboxEvent(@Param("eventId") String eventId, @Param("type") String type,
                          @Param("targetId") long targetId, @Param("payload") String payload);

    @Select("SELECT event_id,payload FROM event_outbox WHERE status='NEW' ORDER BY id LIMIT #{limit}")
    List<EventOutbox> selectPendingOutbox(@Param("limit") int limit);

    @Update("UPDATE event_outbox SET status='SENT',update_time=NOW() WHERE event_id=#{eventId} AND status='NEW'")
    int markOutboxSent(@Param("eventId") String eventId);

    @Update("UPDATE event_outbox SET retry_count=retry_count+1,last_error=#{reason},update_time=NOW() " +
            "WHERE event_id=#{eventId} AND status='NEW'")
    int markOutboxFailed(@Param("eventId") String eventId, @Param("reason") String reason);
}
