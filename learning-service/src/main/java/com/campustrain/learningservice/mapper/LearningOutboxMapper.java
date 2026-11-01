package com.campustrain.learningservice.mapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface LearningOutboxMapper {
    @Insert("INSERT INTO event_outbox (event_id,event_type,user_id,lesson_id,payload,status,retry_count,"
            + "create_time,update_time) VALUES (#{eventId},'LEARNING_COMPLETED',#{userId},#{lessonId},"
            + "#{payload},'NEW',0,#{now},#{now}) ON DUPLICATE KEY UPDATE id=id")
    void insertCompletedEvent(@Param("eventId") String eventId, @Param("userId") long userId,
                              @Param("lessonId") long lessonId, @Param("payload") String payload,
                              @Param("now") LocalDateTime now);

    @Select("SELECT event_id,payload FROM event_outbox WHERE status='NEW' ORDER BY id LIMIT 50")
    List<Map<String, Object>> selectNewEvents();

    @Update("UPDATE event_outbox SET status='SENT',update_time=#{now} WHERE event_id=#{eventId} AND status='NEW'")
    void markSent(@Param("eventId") String eventId, @Param("now") LocalDateTime now);

    @Update("UPDATE event_outbox SET retry_count=retry_count+1,last_error=#{reason},update_time=#{now} "
            + "WHERE event_id=#{eventId} AND status='NEW'")
    void recordFailure(@Param("eventId") String eventId, @Param("reason") String reason,
                       @Param("now") LocalDateTime now);
}
