package com.campustrain.growthservice.service.impl;

import com.campustrain.growthservice.dto.LearningCompletedEvent;
import com.campustrain.growthservice.dto.CommunityActionEvent;
import com.campustrain.growthservice.mapper.GrowthMapper;
import com.campustrain.growthservice.entity.UserPoints;
import com.campustrain.growthservice.service.GrowthService;
import com.campustrain.growthservice.vo.RankView;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** MySQL is authoritative; Redis holds recoverable total and monthly ranking projections. */
@Slf4j
@Service
public class GrowthServiceImpl implements GrowthService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private final GrowthMapper mapper;
    private final StringRedisTemplate redis;
    private final int lessonCompletedPoints;
    private final int questionCreatedPoints;
    private final int answerCreatedPoints;

    @Autowired
    public GrowthServiceImpl(GrowthMapper mapper, StringRedisTemplate redis,
                         @Value("${campustrain.growth.points.lesson-completed:10}") int lessonCompletedPoints,
                         @Value("${campustrain.growth.points.question-created:2}") int questionCreatedPoints,
                         @Value("${campustrain.growth.points.answer-created:5}") int answerCreatedPoints) {
        this.mapper = mapper;
        this.redis = redis;
        this.lessonCompletedPoints = lessonCompletedPoints;
        this.questionCreatedPoints = questionCreatedPoints;
        this.answerCreatedPoints = answerCreatedPoints;
    }

    public GrowthServiceImpl(GrowthMapper mapper, StringRedisTemplate redis, int lessonCompletedPoints) {
        this(mapper, redis, lessonCompletedPoints, 2, 5);
    }

    /** Returns false for a duplicate event, allowing an at-least-once consumer to ACK safely. */
    @Transactional
    public boolean award(LearningCompletedEvent event) {
        if (!"LEARNING_COMPLETED".equals(event.eventType())) {
            log.warn("ignore unsupported growth event, eventId={}, type={}", event.eventId(), event.eventType());
            return false;
        }
        try {
            mapper.insertFlow(event, lessonCompletedPoints, shanghaiDateTime(event.occurredAt()));
            mapper.incrementAccount(event.userId(), lessonCompletedPoints);
        } catch (DuplicateKeyException duplicate) {
            log.warn("duplicate learning event ignored, eventId={}, userId={}, traceId={}", event.eventId(), event.userId(), event.traceId());
            return false;
        }
        try {
            String user = String.valueOf(event.userId());
            redis.opsForZSet().incrementScore(TOTAL_RANKING_KEY, user, lessonCompletedPoints);
            redis.opsForZSet().incrementScore(GrowthService.monthRankingKey(monthOf(event.occurredAt())), user, lessonCompletedPoints);
        } catch (Exception ex) {
            log.warn("Redis ranking projection failed; rebuild will recover it, eventId={}, userId={}, traceId={}",
                    event.eventId(), event.userId(), event.traceId(), ex);
        }
        log.info("points awarded, eventId={}, userId={}, points={}, traceId={}",
                event.eventId(), event.userId(), lessonCompletedPoints, event.traceId());
        return true;
    }

    @Transactional
    public boolean awardCommunity(CommunityActionEvent event) {
        int points = switch (event.actionType()) {
            case "QUESTION_CREATED" -> questionCreatedPoints;
            case "ANSWER_CREATED" -> answerCreatedPoints;
            default -> 0;
        };
        if (!"COMMUNITY_ACTION".equals(event.eventType()) || points == 0 || event.userId() == null
                || event.targetId() == null || event.eventId() == null || event.occurredAt() == null) {
            log.warn("unsupported community event, eventId={}, actionType={}", event.eventId(), event.actionType());
            return false;
        }
        try {
            mapper.insertCommunityFlow(event, points, shanghaiDateTime(event.occurredAt()));
            mapper.incrementAccount(event.userId(), points);
        } catch (DuplicateKeyException duplicate) {
            log.warn("duplicate community event ignored, eventId={}, userId={}, traceId={}",
                    event.eventId(), event.userId(), event.traceId());
            return false;
        }
        try {
            String user = String.valueOf(event.userId());
            redis.opsForZSet().incrementScore(TOTAL_RANKING_KEY, user, points);
            redis.opsForZSet().incrementScore(GrowthService.monthRankingKey(monthOf(event.occurredAt())), user, points);
        } catch (Exception ex) {
            log.warn("community Redis ranking projection failed; rebuild can recover, eventId={}, userId={}, traceId={}",
                    event.eventId(), event.userId(), event.traceId(), ex);
        }
        log.info("community points awarded, eventId={}, userId={}, points={}, traceId={}",
                event.eventId(), event.userId(), points, event.traceId());
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
        for (UserPoints row : mapper.totalPoints()) {
            redis.opsForZSet().add(TOTAL_RANKING_KEY, String.valueOf(row.userId()), row.points());
        }
        if (month != null && !month.isBlank()) {
            String key = GrowthService.monthRankingKey(month);
            redis.delete(key);
            for (UserPoints row : mapper.monthlyPoints(month)) {
                redis.opsForZSet().add(key, String.valueOf(row.userId()), row.points());
            }
        }
        log.info("growth ranking rebuilt from MySQL, month={}", month);
    }

    /** Settlement is idempotent per season; a settled season and unique history rows prevent duplicates. */
    @Transactional
    public void settle(String requestedSeason) {
        String seasonCode = requestedSeason == null || requestedSeason.isBlank()
                ? YearMonth.now(BUSINESS_ZONE).minusMonths(1).toString() : requestedSeason;
        log.info("season settlement started, seasonCode={}", seasonCode);
        mapper.ensureSeason(seasonCode);
        Long seasonId = mapper.lockedSeasonId(seasonCode);
        String current = mapper.seasonStatus(seasonId);
        if ("SETTLED".equals(current)) {
            mapper.recordExecution(seasonCode, "DONE");
            log.info("season already settled, seasonCode={}", seasonCode);
            return;
        }
        mapper.recordExecution(seasonCode, "RUNNING");
        List<UserPoints> ranks = mapper.seasonPoints(seasonCode);
        long rank = 1;
        for (UserPoints row : ranks) {
            mapper.insertHistory(seasonId, seasonCode, row.userId(), row.points(), rank++);
        }
        mapper.settleSeason(seasonId);
        YearMonth next = YearMonth.parse(seasonCode).plusMonths(1);
        mapper.ensureSeason(next.toString());
        mapper.recordExecution(seasonCode, "DONE");
        log.info("season settled, seasonCode={}, rankedUsers={}", seasonCode, ranks.size());
    }

    public static String monthOf(String occurredAt) {
        return shanghaiDateTime(occurredAt).format(DateTimeFormatter.ofPattern("yyyy-MM"));
    }

    public static LocalDateTime shanghaiDateTime(String occurredAt) {
        try {
            return OffsetDateTime.parse(occurredAt).atZoneSameInstant(BUSINESS_ZONE).toLocalDateTime();
        } catch (DateTimeParseException noOffset) {
            // Stage16 emits LocalDateTime in the Asia/Shanghai business timezone.
            return LocalDateTime.parse(occurredAt);
        }
    }
}
