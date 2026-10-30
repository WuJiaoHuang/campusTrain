package com.campustrain.growthservice.mapper;

import com.campustrain.growthservice.dto.LearningCompletedEvent;
import com.campustrain.growthservice.dto.CommunityActionEvent;
import com.campustrain.growthservice.entity.UserPoints;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;

@Repository
public class GrowthMapper {
    private final JdbcTemplate jdbc;

    public GrowthMapper(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void insertFlow(LearningCompletedEvent event, int points, LocalDateTime occurredAt) {
        jdbc.update("INSERT INTO points_flow(event_id,user_id,source_type,source_id,points,occurred_at,create_time) VALUES(?,?,?,?,?,?,NOW())",
                event.eventId(), event.userId(), "LEARNING_COMPLETED", event.lessonId(), points, occurredAt);
    }

    public void insertCommunityFlow(CommunityActionEvent event, int points, LocalDateTime occurredAt) {
        jdbc.update("INSERT INTO points_flow(event_id,user_id,source_type,source_id,points,occurred_at,create_time) VALUES(?,?,?,?,?,?,NOW())",
                event.eventId(), event.userId(), event.actionType(), event.targetId(), points, occurredAt);
    }

    public void incrementAccount(long userId, int points) {
        jdbc.update("INSERT INTO points_account(user_id,total_points,update_time) VALUES(?,?,NOW()) ON DUPLICATE KEY UPDATE total_points=total_points+VALUES(total_points),update_time=NOW()",
                userId, points);
    }

    public List<UserPoints> totalPoints() {
        List<UserPoints> result = new ArrayList<>();
        jdbc.query("SELECT user_id,total_points FROM points_account", (RowCallbackHandler) rs ->
                result.add(new UserPoints(rs.getLong("user_id"), rs.getLong("total_points"))));
        return result;
    }

    public List<UserPoints> monthlyPoints(String month) {
        List<UserPoints> result = new ArrayList<>();
        jdbc.query("SELECT user_id,SUM(points) AS points FROM points_flow WHERE DATE_FORMAT(occurred_at,'%Y-%m')=? GROUP BY user_id",
                ps -> ps.setString(1, month),
                (RowCallbackHandler) rs -> result.add(new UserPoints(rs.getLong("user_id"), rs.getLong("points"))));
        return result;
    }

    public void ensureSeason(String code) {
        jdbc.update("INSERT IGNORE INTO ranking_season(season_code,start_time,end_time,status) VALUES(?,STR_TO_DATE(CONCAT(?,'-01'),'%Y-%m-%d'),LAST_DAY(STR_TO_DATE(CONCAT(?,'-01'),'%Y-%m-%d')),'ACTIVE')",
                code, code, code);
    }

    public Long lockedSeasonId(String code) {
        return jdbc.queryForObject("SELECT id FROM ranking_season WHERE season_code=? FOR UPDATE", Long.class, code);
    }

    public String seasonStatus(long id) {
        return jdbc.queryForObject("SELECT status FROM ranking_season WHERE id=?", String.class, id);
    }

    public List<UserPoints> seasonPoints(String code) {
        List<UserPoints> result = new ArrayList<>();
        jdbc.query("SELECT user_id,SUM(points) AS points FROM points_flow WHERE DATE_FORMAT(occurred_at,'%Y-%m')=? GROUP BY user_id ORDER BY points DESC,user_id ASC",
                ps -> ps.setString(1, code),
                (RowCallbackHandler) rs -> result.add(new UserPoints(rs.getLong("user_id"), rs.getLong("points"))));
        return result;
    }

    public void insertHistory(long seasonId, String code, long userId, long points, long rank) {
        jdbc.update("INSERT IGNORE INTO ranking_history(season_id,season_code,user_id,points,rank_no,create_time) VALUES(?,?,?,?,?,NOW())",
                seasonId, code, userId, points, rank);
    }

    public void settleSeason(long id) {
        jdbc.update("UPDATE ranking_season SET status='SETTLED',settled_at=NOW() WHERE id=?", id);
    }

    public void recordExecution(String code, String status) {
        jdbc.update("INSERT INTO job_execution_record(job_name,biz_key,status,start_time,finish_time) VALUES('growthSeasonSettlementJob',?,?,NOW(),IF(?='DONE',NOW(),NULL)) ON DUPLICATE KEY UPDATE status=VALUES(status),finish_time=VALUES(finish_time)",
                code, status, status);
    }
}
