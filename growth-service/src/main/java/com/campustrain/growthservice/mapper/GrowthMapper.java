package com.campustrain.growthservice.mapper;

import com.campustrain.growthservice.dto.CommunityActionEvent;
import com.campustrain.growthservice.dto.LearningCompletedEvent;
import com.campustrain.growthservice.entity.UserPoints;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface GrowthMapper {
    default void insertFlow(LearningCompletedEvent event, int points, LocalDateTime occurredAt) {
        insertLearningFlowValues(event.eventId(), event.userId(), event.lessonId(), points, occurredAt);
    }

    @Insert("INSERT INTO points_flow(event_id,user_id,source_type,source_id,points,occurred_at,create_time) "
            + "VALUES(#{eventId},#{userId},'LEARNING_COMPLETED',#{lessonId},#{points},#{occurredAt},NOW())")
    void insertLearningFlowValues(@Param("eventId") String eventId, @Param("userId") long userId,
                                  @Param("lessonId") long lessonId, @Param("points") int points,
                                  @Param("occurredAt") LocalDateTime occurredAt);

    default void insertCommunityFlow(CommunityActionEvent event, int points, LocalDateTime occurredAt) {
        insertCommunityFlowValues(event.eventId(), event.userId(), event.actionType(), event.targetId(), points, occurredAt);
    }

    @Insert("INSERT INTO points_flow(event_id,user_id,source_type,source_id,points,occurred_at,create_time) "
            + "VALUES(#{eventId},#{userId},#{actionType},#{targetId},#{points},#{occurredAt},NOW())")
    void insertCommunityFlowValues(@Param("eventId") String eventId, @Param("userId") long userId,
                                   @Param("actionType") String actionType, @Param("targetId") long targetId,
                                   @Param("points") int points, @Param("occurredAt") LocalDateTime occurredAt);

    @Insert("INSERT INTO points_account(user_id,total_points,update_time) VALUES(#{userId},#{points},NOW()) "
            + "ON DUPLICATE KEY UPDATE total_points=total_points+VALUES(total_points),update_time=NOW()")
    void incrementAccount(@Param("userId") long userId, @Param("points") int points);

    @Select("SELECT user_id AS userId,total_points AS points FROM points_account")
    @ConstructorArgs({@Arg(column = "userId", javaType = long.class), @Arg(column = "points", javaType = long.class)})
    List<UserPoints> selectTotalPoints();

    @Select("SELECT user_id AS userId,SUM(points) AS points FROM points_flow "
            + "WHERE DATE_FORMAT(occurred_at,'%Y-%m')=#{month} GROUP BY user_id")
    @ConstructorArgs({@Arg(column = "userId", javaType = long.class), @Arg(column = "points", javaType = long.class)})
    List<UserPoints> selectMonthlyPoints(@Param("month") String month);

    @Insert("INSERT IGNORE INTO ranking_season(season_code,start_time,end_time,status) "
            + "VALUES(#{code},STR_TO_DATE(CONCAT(#{code},'-01'),'%Y-%m-%d'),"
            + "LAST_DAY(STR_TO_DATE(CONCAT(#{code},'-01'),'%Y-%m-%d')),'ACTIVE')")
    void insertSeasonIfAbsent(@Param("code") String code);

    @Select("SELECT id FROM ranking_season WHERE season_code=#{code} FOR UPDATE")
    Long selectSeasonIdForUpdate(@Param("code") String code);

    @Select("SELECT status FROM ranking_season WHERE id=#{seasonId}")
    String selectSeasonStatus(@Param("seasonId") long seasonId);

    @Select("SELECT user_id AS userId,SUM(points) AS points FROM points_flow "
            + "WHERE DATE_FORMAT(occurred_at,'%Y-%m')=#{code} GROUP BY user_id ORDER BY points DESC,user_id ASC")
    @ConstructorArgs({@Arg(column = "userId", javaType = long.class), @Arg(column = "points", javaType = long.class)})
    List<UserPoints> selectSeasonPoints(@Param("code") String code);

    @Insert("INSERT IGNORE INTO ranking_history(season_id,season_code,user_id,points,rank_no,create_time) "
            + "VALUES(#{seasonId},#{code},#{userId},#{points},#{rank},NOW())")
    void insertHistory(@Param("seasonId") long seasonId, @Param("code") String code,
                       @Param("userId") long userId, @Param("points") long points, @Param("rank") long rank);

    @Update("UPDATE ranking_season SET status='SETTLED',settled_at=NOW() WHERE id=#{seasonId}")
    void updateSeasonSettled(@Param("seasonId") long seasonId);

    @Insert("INSERT INTO job_execution_record(job_name,biz_key,status,start_time,finish_time) "
            + "VALUES('growthSeasonSettlementJob',#{code},#{status},NOW(),IF(#{status}='DONE',NOW(),NULL)) "
            + "ON DUPLICATE KEY UPDATE status=VALUES(status),finish_time=VALUES(finish_time)")
    void upsertJobExecution(@Param("code") String code, @Param("status") String status);
}
