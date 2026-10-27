package com.campustrain.learningservice;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import com.campustrain.learningservice.client.EnrollmentClient;
import com.campustrain.learningservice.client.TrainingClient;
import com.campustrain.learningservice.client.dto.Lesson;
import com.campustrain.learningservice.client.dto.RemoteResult;
import com.campustrain.learningservice.context.RequestIdentity;
import com.campustrain.learningservice.controller.LearningController;
import com.campustrain.learningservice.service.LearningProgressStore;
import com.campustrain.learningservice.service.LearningService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

@EnabledIfEnvironmentVariable(named = "STAGE16_MYSQL_INTEGRATION", matches = "true")
class ProgressPersistenceIntegrationTest {
    private static final long TEST_USER = 16900001L;
    private static final long TEST_LESSON = 16900001L;
    private JdbcTemplate jdbc;
    private TransactionTemplate tx;
    private LearningProgressStore store;
    private Method persist;
    @BeforeEach void setup() throws Exception {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setUrl("jdbc:mysql://" + System.getenv().getOrDefault("MYSQL_HOST", "192.168.10.102") + ":3306/campus_learning?serverTimezone=Asia/Shanghai");
        ds.setUsername(System.getenv().getOrDefault("MYSQL_USERNAME", "root")); ds.setPassword(System.getenv().getOrDefault("MYSQL_PASSWORD", "123"));
        jdbc = new JdbcTemplate(ds); tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        store = new LearningProgressStore(mock(StringRedisTemplate.class), jdbc, tx, new ObjectMapper(), 100);
        persist = LearningProgressStore.class.getDeclaredMethod("persist", Map.class); persist.setAccessible(true);
        cleanup();
    }
    @AfterEach void cleanup() {
        jdbc.update("DELETE FROM event_outbox WHERE user_id=? AND lesson_id=?", TEST_USER, TEST_LESSON);
        jdbc.update("DELETE FROM learning_record WHERE user_id=? AND lesson_id=?", TEST_USER, TEST_LESSON);
    }
    private void save(int seconds, int completed) {
        Map<Object, Object> values = new HashMap<>();
        values.put("userId", Long.toString(TEST_USER)); values.put("lessonId", Long.toString(TEST_LESSON));
        values.put("trainingId", "16900001"); values.put("courseId", "16900001"); values.put("chapterId", "16900001");
        values.put("progressSeconds", Integer.toString(seconds)); values.put("durationSeconds", "100");
        values.put("completed", Integer.toString(completed)); values.put("traceId", "stage16-integration-test");
        values.put("firstStartTime", "2026-01-01T00:00:00"); values.put("updatedAt", "2026-01-01T00:00:00Z");
        tx.executeWithoutResult(status -> { try { persist.invoke(store, values); } catch (Exception e) { throw new IllegalStateException(e); } });
    }
    @Test void databaseNeverRegressesAndCompletionCreatesOnlyOneEvent() {
        save(30, 0); save(95, 1); save(20, 0);
        assertEquals(95, jdbc.queryForObject("SELECT progress_seconds FROM learning_record WHERE user_id=? AND lesson_id=?", Integer.class, TEST_USER, TEST_LESSON));
        save(100, 1);
        Map<String, Object> row = jdbc.queryForMap("SELECT progress_seconds,status,completed FROM learning_record WHERE user_id=? AND lesson_id=?", TEST_USER, TEST_LESSON);
        assertEquals(100, ((Number) row.get("progress_seconds")).intValue());
        assertEquals(2, ((Number) row.get("status")).intValue());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM event_outbox WHERE user_id=? AND lesson_id=? AND event_type='LEARNING_COMPLETED'", Integer.class, TEST_USER, TEST_LESSON));
    }
    @Test void recordAndOutboxRollbackTogether() {
        tx.executeWithoutResult(status -> {
            save(90, 1);
            status.setRollbackOnly();
        });
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM learning_record WHERE user_id=? AND lesson_id=?", Integer.class, TEST_USER, TEST_LESSON));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM event_outbox WHERE user_id=? AND lesson_id=?", Integer.class, TEST_USER, TEST_LESSON));
    }
    @Test
    @EnabledIfEnvironmentVariable(named = "STAGE16_REDIS_INTEGRATION", matches = "true")
    void redisMissRecoversPersistedProgressAndCompletion() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(System.getenv().getOrDefault("REDIS_HOST", "192.168.10.102"),
                Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6379")));
        config.setPassword(System.getenv().getOrDefault("REDIS_PASSWORD", "123456"));
        LettuceConnectionFactory factory = new LettuceConnectionFactory(config);
        factory.afterPropertiesSet(); factory.start();
        StringRedisTemplate redis = new StringRedisTemplate(factory); redis.afterPropertiesSet();
        String key = LearningProgressStore.key(TEST_USER, TEST_LESSON);
        try {
            redis.delete(key);
            LearningProgressStore actual = new LearningProgressStore(redis, jdbc, tx, new ObjectMapper(), 100);
            Lesson lesson = new Lesson(TEST_LESSON, 0, 100, 16900001L, 16900001L, 16900001L, true);
            assertEquals("0", actual.peek(TEST_USER, lesson, "test").get("progressSeconds"));
            assertFalse(Boolean.TRUE.equals(redis.hasKey(key)));
            assertFalse(Boolean.TRUE.equals(redis.opsForSet().isMember(LearningProgressStore.DIRTY, TEST_USER + ":" + TEST_LESSON)));
            save(95, 1);
            Map<Object, Object> recovered = actual.read(TEST_USER, lesson, "test");
            assertEquals("95", recovered.get("progressSeconds"));
            assertEquals("1", recovered.get("completed"));
            assertNotNull(recovered.get("firstStartTime"));
            assertFalse(Boolean.TRUE.equals(redis.opsForSet().isMember(LearningProgressStore.DIRTY, TEST_USER + ":" + TEST_LESSON)));
        } finally {
            redis.delete(key);
            redis.opsForSet().remove(LearningProgressStore.DIRTY, TEST_USER + ":" + TEST_LESSON);
            factory.destroy();
        }
    }
    @Test
    @EnabledIfEnvironmentVariable(named = "STAGE16_REDIS_INTEGRATION", matches = "true")
    void tenHttpReportsAreCoalescedIntoOneDatabaseWrite() throws Exception {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(System.getenv().getOrDefault("REDIS_HOST", "192.168.10.102"),
                Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6379")));
        config.setPassword(System.getenv().getOrDefault("REDIS_PASSWORD", "123456"));
        LettuceConnectionFactory factory = new LettuceConnectionFactory(config); factory.afterPropertiesSet(); factory.start();
        StringRedisTemplate redis = new StringRedisTemplate(factory); redis.afterPropertiesSet();
        String key = LearningProgressStore.key(TEST_USER, TEST_LESSON), member = TEST_USER + ":" + TEST_LESSON;
        try {
            Assumptions.assumeTrue(Long.valueOf(0).equals(redis.opsForSet().size(LearningProgressStore.DIRTY)), "dirty set must be empty for isolation");
            redis.delete(key);
            JdbcTemplate counted = spy(jdbc);
            LearningProgressStore actual = new LearningProgressStore(redis, counted, tx, new ObjectMapper(), 100);
            TrainingClient training = mock(TrainingClient.class); EnrollmentClient enrollment = mock(EnrollmentClient.class);
            Lesson lesson = new Lesson(TEST_LESSON, 0, 100, 16900001L, 16900001L, 16900001L, true);
            when(training.lesson(TEST_LESSON)).thenReturn(new RemoteResult<>(200, "success", lesson));
            when(enrollment.active(16900001L, TEST_USER)).thenReturn(new RemoteResult<>(200, "success", true));
            MockMvc mvc = MockMvcBuilders.standaloneSetup(new LearningController(new LearningService(training, enrollment, actual, 0.9)))
                    .addFilters(new RequestIdentity()).build();
            for (int i = 1; i <= 10; i++) {
                mvc.perform(post("/learning/lessons/{id}/progress", TEST_LESSON).header("X-User-Id", TEST_USER)
                        .header("X-User-Role", "STUDENT").contentType("application/json")
                        .content("{\"progressSeconds\":" + i + "}")).andExpect(status().isOk());
            }
            verify(counted, never()).update(startsWith("INSERT INTO learning_record"), any(Object[].class));
            actual.flush();
            verify(counted, times(1)).update(startsWith("INSERT INTO learning_record"), any(Object[].class));
            assertEquals(10, jdbc.queryForObject("SELECT progress_seconds FROM learning_record WHERE user_id=? AND lesson_id=?", Integer.class, TEST_USER, TEST_LESSON));
            assertFalse(Boolean.TRUE.equals(redis.opsForSet().isMember(LearningProgressStore.DIRTY, member)));
        } finally {
            redis.delete(key); redis.opsForSet().remove(LearningProgressStore.DIRTY, member); factory.destroy();
        }
    }
}
