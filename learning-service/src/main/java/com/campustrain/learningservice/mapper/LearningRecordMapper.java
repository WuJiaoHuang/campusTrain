package com.campustrain.learningservice.mapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface LearningRecordMapper {
    @Select("SELECT progress_seconds,duration_seconds,completed,first_start_time,last_learn_time "
            + "FROM learning_record WHERE user_id=#{userId} AND lesson_id=#{lessonId}")
    List<Map<String, Object>> selectProgress(@Param("userId") long userId, @Param("lessonId") long lessonId);

    @Select("SELECT progress_seconds,duration_seconds,completed,last_learn_time "
            + "FROM learning_record WHERE user_id=#{userId} AND lesson_id=#{lessonId}")
    List<Map<String, Object>> selectPersistedProgress(@Param("userId") long userId, @Param("lessonId") long lessonId);

    @Select("SELECT lesson_id FROM learning_record WHERE user_id=#{userId} AND completed=1")
    List<Long> selectCompletedLessonIds(@Param("userId") long userId);

    @Insert("INSERT INTO learning_record (user_id,training_id,course_id,chapter_id,lesson_id,"
            + "progress_seconds,duration_seconds,status,completed,first_start_time,last_learn_time,"
            + "complete_time,create_time,update_time) VALUES (#{userId},#{trainingId},#{courseId},"
            + "#{chapterId},#{lessonId},#{progressSeconds},#{durationSeconds},#{status},#{completed},"
            + "#{firstStartTime},#{lastLearnTime},#{completeTime},#{createTime},#{updateTime}) "
            + "ON DUPLICATE KEY UPDATE progress_seconds=GREATEST(progress_seconds,VALUES(progress_seconds)),"
            + "completed=GREATEST(completed,VALUES(completed)),status=GREATEST(status,VALUES(status)),"
            + "complete_time=COALESCE(complete_time,VALUES(complete_time)),"
            + "last_learn_time=GREATEST(last_learn_time,VALUES(last_learn_time)),update_time=VALUES(update_time)")
    void upsertProgress(@Param("userId") long userId, @Param("trainingId") long trainingId,
                        @Param("courseId") long courseId, @Param("chapterId") long chapterId,
                        @Param("lessonId") long lessonId, @Param("progressSeconds") long progressSeconds,
                        @Param("durationSeconds") long durationSeconds, @Param("status") int status,
                        @Param("completed") int completed, @Param("firstStartTime") LocalDateTime firstStartTime,
                        @Param("lastLearnTime") LocalDateTime lastLearnTime,
                        @Param("completeTime") LocalDateTime completeTime, @Param("createTime") LocalDateTime createTime,
                        @Param("updateTime") LocalDateTime updateTime);
}
