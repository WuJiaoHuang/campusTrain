package com.campustrain.growthservice;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** MySQL is authoritative; Redis holds recoverable total and monthly ranking projections. */
@Slf4j
@Service
public class GrowthService {
    public static final String TOTAL_RANKING_KEY = "campustrain:growth:ranking:total";
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;
    private final int lessonCompletedPoints;

    public GrowthService(JdbcTemplate jdbc, StringRedisTemplate redis,
                         @Value("${campustrain.growth.points.lesson-completed:10}") int lessonCompletedPoints) {
        this.jdbc = jdbc;
        this.redis = redis;
        this.lessonCompletedPoints = lessonCompletedPoints;
    }

    /** Returns false for a duplicate event, allowing an at-least-once consumer to ACK safely. */
    @Transactional
    public boolean award(LearningCompletedEvent event) {
        if (!"LEARNING_COMPLETED".equals(event.eventType())) {
            log.warn("ignore unsupported growth event, eventId={}, type={}", event.eventId(), event.eventType());
            return false;
        }
        try {
            jdbc.update("INSERT INTO points_flow(event_id,user_id,source_type,source_id,points,occurred_at,create_time) VALUES(?,?,?,?,?,?,NOW())",
                    event.eventId(), event.userId(), "LEARNING_COMPLETED", event.lessonId(), lessonCompletedPoints, shanghaiDateTime(event.occurredAt()));
            jdbc.update("INSERT INTO points_account(user_id,total_points,update_time) VALUES(?,?,NOW()) ON DUPLICATE KEY UPDATE total_points=total_points+VALUES(total_points),update_time=NOW()",
                    event.userId(), lessonCompletedPoints);
        } catch (DuplicateKeyException duplicate) {
            log.warn("duplicate learning event ignored, eventId={}, userId={}, traceId={}", event.eventId(), event.userId(), event.traceId());
            return false;
        }
        try {
            String user = String.valueOf(event.userId());
            redis.opsForZSet().incrementScore(TOTAL_RANKING_KEY, user, lessonCompletedPoints);
            redis.opsForZSet().incrementScore(monthRankingKey(monthOf(event.occurredAt())), user, lessonCompletedPoints);
        } catch (Exception ex) {
            log.warn("Redis ranking projection failed; rebuild will recover it, eventId={}, userId={}, traceId={}",
                    event.eventId(), event.userId(), event.traceId(), ex);
        }
        log.info("points awarded, eventId={}, userId={}, points={}, traceId={}",
                event.eventId(), event.userId(), lessonCompletedPoints, event.traceId());
        return true;
    }

    public List<RankView> top(String key, int limit) {
        var users = redis.opsForZSet().reverseRange(key, 0, limit - 1);
        if (users == null || users.isEmpty()) return List.of();
        List<RankView> result = new ArrayList<>();
        long rank = 1;
        for (String user : users) {
            Double score = redis.opsForZSet().score(key, user);
            if (score != null) result.add(new RankView(rank++, Long.parseLong(user), score.longValue()));
        }
        return result;
    }

    public RankView mine(String key, long userId) {
        String member = String.valueOf(userId);
        Long rank = redis.opsForZSet().reverseRank(key, member);
        Double score = redis.opsForZSet().score(key, member);
        return rank == null || score == null ? null : new RankView(rank + 1, userId, score.longValue());
    }

    /** Rebuilds Redis from MySQL without changing the points facts. */
    public void rebuild(String month) {
        redis.delete(TOTAL_RANKING_KEY);
        jdbc.query("SELECT user_id,total_points FROM points_account", (RowCallbackHandler) rs -> redis.opsForZSet().add(
                TOTAL_RANKING_KEY, String.valueOf(rs.getLong("user_id")), rs.getDouble("total_points")));
        if (month != null && !month.isBlank()) {
            String key = monthRankingKey(month);
            redis.delete(key);
            jdbc.query("SELECT user_id,SUM(points) AS points FROM points_flow WHERE DATE_FORMAT(occurred_at,'%Y-%m')=? GROUP BY user_id",
                    ps -> ps.setString(1, month),
                    (RowCallbackHandler) rs -> redis.opsForZSet().add(key, String.valueOf(rs.getLong("user_id")), rs.getDouble("points")));
        }
        log.info("growth ranking rebuilt from MySQL, month={}", month);
    }

    /** Settlement is idempotent per season; a settled season and unique history rows prevent duplicates. */
    @Transactional
    public void settle(String requestedSeason) {
        String seasonCode = requestedSeason == null || requestedSeason.isBlank()
                ? YearMonth.now(BUSINESS_ZONE).minusMonths(1).toString() : requestedSeason;
        log.info("season settlement started, seasonCode={}", seasonCode);
        jdbc.update("INSERT IGNORE INTO ranking_season(season_code,start_time,end_time,status) VALUES(?,STR_TO_DATE(CONCAT(?,'-01'),'%Y-%m-%d'),LAST_DAY(STR_TO_DATE(CONCAT(?,'-01'),'%Y-%m-%d')),'ACTIVE')",
                seasonCode, seasonCode, seasonCode);
        Long seasonId = jdbc.queryForObject("SELECT id FROM ranking_season WHERE season_code=? FOR UPDATE", Long.class, seasonCode);
        String current = jdbc.queryForObject("SELECT status FROM ranking_season WHERE id=?", String.class, seasonId);
        if ("SETTLED".equals(current)) {
            recordExecution(seasonCode, "DONE");
            log.info("season already settled, seasonCode={}", seasonCode);
            return;
        }
        recordExecution(seasonCode, "RUNNING");
        List<RankView> ranks = new ArrayList<>();
        jdbc.query("SELECT user_id,SUM(points) AS points FROM points_flow WHERE DATE_FORMAT(occurred_at,'%Y-%m')=? GROUP BY user_id ORDER BY points DESC,user_id ASC",
                ps -> ps.setString(1, seasonCode),
                (RowCallbackHandler) rs -> ranks.add(new RankView(ranks.size() + 1L, rs.getLong("user_id"), rs.getLong("points"))));
        for (RankView rank : ranks) {
            jdbc.update("INSERT IGNORE INTO ranking_history(season_id,season_code,user_id,points,rank_no,create_time) VALUES(?,?,?,?,?,NOW())",
                    seasonId, seasonCode, rank.userId(), rank.points(), rank.rank());
        }
        jdbc.update("UPDATE ranking_season SET status='SETTLED',settled_at=NOW() WHERE id=?", seasonId);
        YearMonth next = YearMonth.parse(seasonCode).plusMonths(1);
        jdbc.update("INSERT IGNORE INTO ranking_season(season_code,start_time,end_time,status) VALUES(?,STR_TO_DATE(CONCAT(?,'-01'),'%Y-%m-%d'),LAST_DAY(STR_TO_DATE(CONCAT(?,'-01'),'%Y-%m-%d')),'ACTIVE')",
                next.toString(), next.toString(), next.toString());
        recordExecution(seasonCode, "DONE");
        log.info("season settled, seasonCode={}, rankedUsers={}", seasonCode, ranks.size());
    }

    private void recordExecution(String seasonCode, String status) {
        jdbc.update("INSERT INTO job_execution_record(job_name,biz_key,status,start_time,finish_time) VALUES('growthSeasonSettlementJob',?,?,NOW(),IF(?='DONE',NOW(),NULL)) ON DUPLICATE KEY UPDATE status=VALUES(status),finish_time=VALUES(finish_time)",
                seasonCode, status, status);
    }

    static String monthOf(String occurredAt) {
        return shanghaiDateTime(occurredAt).format(DateTimeFormatter.ofPattern("yyyy-MM"));
    }

    static LocalDateTime shanghaiDateTime(String occurredAt) {
        try {
            return OffsetDateTime.parse(occurredAt).atZoneSameInstant(BUSINESS_ZONE).toLocalDateTime();
        } catch (DateTimeParseException noOffset) {
            // Stage16 emits LocalDateTime in the Asia/Shanghai business timezone.
            return LocalDateTime.parse(occurredAt);
        }
    }
    public static String monthRankingKey(String month) { return "campustrain:growth:ranking:month:" + month; }
}
