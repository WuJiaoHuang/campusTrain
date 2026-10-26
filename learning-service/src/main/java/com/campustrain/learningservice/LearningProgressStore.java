package com.campustrain.learningservice;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.HashSet;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
public class LearningProgressStore {
    static final String DIRTY = "campustrain:learning:dirty";
    private final StringRedisTemplate redis;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final ObjectMapper json;
    private final int batchSize;
    private final DefaultRedisScript<Long> initialize = script("lua/initialize.lua", Long.class);
    @SuppressWarnings("rawtypes")
    private final DefaultRedisScript<List> progress = script("lua/progress.lua", List.class);
    private final DefaultRedisScript<Long> complete = script("lua/complete.lua", Long.class);
    private final DefaultRedisScript<Long> clear = script("lua/clear-dirty.lua", Long.class);

    public LearningProgressStore(StringRedisTemplate redis, JdbcTemplate jdbc, TransactionTemplate transaction,
                                 ObjectMapper json, @Value("${campustrain.learning.flush-batch-size:100}") int batchSize) {
        this.redis = redis; this.jdbc = jdbc; this.transaction = transaction; this.json = json;
        this.batchSize = Math.max(1, batchSize);
    }

    static String key(long userId, long lessonId) { return "campustrain:learning:progress:" + userId + ":" + lessonId; }
    private static String member(long userId, long lessonId) { return userId + ":" + lessonId; }
    private static <T> DefaultRedisScript<T> script(String path, Class<T> result) {
        DefaultRedisScript<T> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(path)); script.setResultType(result); return script;
    }

    public Map<Object, Object> read(long userId, RemoteClients.Lesson lesson, String traceId) {
        String key = key(userId, lesson.lessonId());
        Map<Object, Object> values = redis.opsForHash().entries(key);
        if (!values.isEmpty()) return values;
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT progress_seconds,duration_seconds,completed,first_start_time,last_learn_time FROM learning_record WHERE user_id=? AND lesson_id=?", userId, lesson.lessonId());
        Map<String, Object> row = rows.isEmpty() ? null : rows.get(0);
        int current = row == null ? 0 : ((Number) row.get("progress_seconds")).intValue();
        int duration = row == null ? (lesson.videoDuration() == null ? 0 : lesson.videoDuration()) : ((Number) row.get("duration_seconds")).intValue();
        int completed = row == null ? 0 : flag(row.get("completed"));
        LocalDateTime firstStart = row == null ? LocalDateTime.now() : localDateTime(row.get("first_start_time"));
        String updatedAt = row == null ? Instant.now().toString() : localDateTime(row.get("last_learn_time")).atZone(ZoneId.systemDefault()).toInstant().toString();
        redis.execute(initialize, List.of(key, DIRTY), String.valueOf(userId), String.valueOf(lesson.lessonId()),
                String.valueOf(lesson.trainingId()), String.valueOf(lesson.courseId()), String.valueOf(lesson.chapterId()),
                String.valueOf(current), String.valueOf(duration), String.valueOf(completed), updatedAt,
                traceId == null ? "" : traceId, row == null ? "1" : "0", firstStart.toString());
        return redis.opsForHash().entries(key);
    }

    public Map<Object, Object> peek(long userId, RemoteClients.Lesson lesson, String traceId) {
        Map<Object, Object> values = redis.opsForHash().entries(key(userId, lesson.lessonId()));
        if (!values.isEmpty()) return values;
        if (!persisted(userId, lesson.lessonId()).isEmpty()) return read(userId, lesson, traceId);
        values = redis.opsForHash().entries(key(userId, lesson.lessonId()));
        if (!values.isEmpty()) return values;
        return Map.of("progressSeconds", "0", "durationSeconds",
                String.valueOf(lesson.videoDuration() == null ? 0 : lesson.videoDuration()), "completed", "0", "updatedAt", "");
    }

    public long update(long userId, RemoteClients.Lesson lesson, int seconds, double ratio, String traceId) {
        read(userId, lesson, traceId);
        int threshold = (int) Math.ceil(lesson.videoDuration() * ratio);
        List<?> result = redis.execute(progress, List.of(key(userId, lesson.lessonId()), DIRTY), member(userId, lesson.lessonId()),
                String.valueOf(seconds), String.valueOf(threshold), Instant.now().toString(), traceId == null ? "" : traceId);
        if (result == null || result.isEmpty() || ((Number) result.get(0)).longValue() < 0) throw new IllegalStateException("进度初始化失败");
        return ((Number) result.get(0)).longValue();
    }

    public long complete(long userId, RemoteClients.Lesson lesson, String traceId) {
        read(userId, lesson, traceId);
        Long result = redis.execute(complete, List.of(key(userId, lesson.lessonId()), DIRTY), member(userId, lesson.lessonId()),
                Instant.now().toString(), traceId == null ? "" : traceId);
        if (result == null || result < 0) throw new IllegalStateException("进度初始化失败");
        return result;
    }

    public Map<String, Object> persisted(long userId, long lessonId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT progress_seconds,duration_seconds,completed,last_learn_time FROM learning_record WHERE user_id=? AND lesson_id=?", userId, lessonId);
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    public long countCompleted(long userId, List<Long> lessonIds) {
        if (lessonIds.isEmpty()) return 0;
        List<Long> persisted = jdbc.queryForList("SELECT lesson_id FROM learning_record WHERE user_id=? AND completed=1", Long.class, userId);
        Set<Long> completed = new HashSet<>(persisted);
        try {
            for (Long id : lessonIds) {
                Object flag = redis.opsForHash().get(key(userId, id), "completed");
                if ("1".equals(flag)) completed.add(id);
            }
        } catch (Exception e) { log.warn("Redis completion lookup failed; using persisted progress, userId={}", userId, e); }
        return lessonIds.stream().filter(completed::contains).count();
    }

    @Scheduled(fixedDelayString = "${campustrain.learning.flush-interval-ms:5000}")
    public void flush() {
        try {
            Set<String> members = redis.opsForSet().distinctRandomMembers(DIRTY, batchSize);
            if (members == null || members.isEmpty()) return;
            int saved = 0;
            for (String member : members) {
                try {
                    String[] ids = member.split(":", -1);
                    if (ids.length != 2) throw new IllegalStateException("invalid dirty member");
                    String key = key(Long.parseLong(ids[0]), Long.parseLong(ids[1]));
                    Map<Object, Object> snapshot = redis.opsForHash().entries(key);
                    if (snapshot.isEmpty()) { log.error("dirty progress missing, member={}", member); continue; }
                    transaction.executeWithoutResult(status -> persist(snapshot));
                    redis.execute(clear, List.of(key, DIRTY), member, value(snapshot, "revision"));
                    saved++;
                } catch (Exception e) { log.error("learning progress flush failed, member={}", member, e); }
            }
            log.info("learning progress batch persisted, count={}", saved);
        } catch (Exception e) { log.error("learning progress batch unavailable", e); }
    }

    private void persist(Map<Object, Object> s) {
        long userId = number(s, "userId"), lessonId = number(s, "lessonId");
        int done = (int) number(s, "completed");
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime learnedAt = LocalDateTime.ofInstant(Instant.parse(value(s, "updatedAt")), ZoneId.systemDefault());
        LocalDateTime firstStart = s.containsKey("firstStartTime") ? LocalDateTime.parse(value(s, "firstStartTime")) : learnedAt;
        jdbc.update("INSERT INTO learning_record (user_id,training_id,course_id,chapter_id,lesson_id,progress_seconds,duration_seconds,status,completed,first_start_time,last_learn_time,complete_time,create_time,update_time) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE progress_seconds=GREATEST(progress_seconds,VALUES(progress_seconds)),completed=GREATEST(completed,VALUES(completed)),status=GREATEST(status,VALUES(status)),complete_time=COALESCE(complete_time,VALUES(complete_time)),last_learn_time=GREATEST(last_learn_time,VALUES(last_learn_time)),update_time=VALUES(update_time)",
                userId, number(s, "trainingId"), number(s, "courseId"), number(s, "chapterId"), lessonId,
                number(s, "progressSeconds"), number(s, "durationSeconds"), done == 1 ? 2 : 1, done,
                firstStart, learnedAt, done == 1 ? learnedAt : null, now, now);
        if (done == 1) {
            String eventId = UUID.randomUUID().toString();
            String payload = json.writeValueAsString(Map.of("eventId", eventId, "eventType", "LEARNING_COMPLETED", "userId", userId,
                    "trainingId", number(s, "trainingId"), "courseId", number(s, "courseId"), "lessonId", lessonId,
                    "occurredAt", now.toString(), "traceId", value(s, "traceId")));
            jdbc.update("INSERT INTO event_outbox (event_id,event_type,user_id,lesson_id,payload,status,retry_count,create_time,update_time) VALUES (?,?,?,?,?,'NEW',0,?,?) ON DUPLICATE KEY UPDATE id=id",
                    eventId, "LEARNING_COMPLETED", userId, lessonId, payload, now, now);
        }
    }
    private static String value(Map<Object, Object> s, String key) {
        Object value = s.get(key); if (value == null) throw new IllegalStateException("missing " + key); return value.toString();
    }
    private static long number(Map<Object, Object> s, String key) { return Long.parseLong(value(s, key)); }
    private static int flag(Object value) { return value instanceof Boolean b ? (b ? 1 : 0) : ((Number) value).intValue(); }
    private static LocalDateTime localDateTime(Object value) {
        if (value instanceof LocalDateTime local) return local;
        if (value instanceof Timestamp timestamp) return timestamp.toLocalDateTime();
        throw new IllegalStateException("unexpected SQL timestamp type");
    }
}
