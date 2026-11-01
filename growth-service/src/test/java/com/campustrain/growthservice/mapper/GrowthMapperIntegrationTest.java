package com.campustrain.growthservice.mapper;

import static org.junit.jupiter.api.Assertions.*;

import com.campustrain.growthservice.dto.LearningCompletedEvent;
import com.campustrain.growthservice.entity.UserPoints;
import java.time.LocalDateTime;
import java.util.UUID;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

@EnabledIfEnvironmentVariable(named = "GROWTH_MYSQL_INTEGRATION", matches = "true")
class GrowthMapperIntegrationTest {
    @Test
    void mapperWritesAndReadsPointsWithoutJdbcTemplate() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl("jdbc:mysql://" + System.getenv().getOrDefault("MYSQL_HOST", "192.168.10.102")
                + ":3306/campus_growth?serverTimezone=Asia/Shanghai");
        dataSource.setUsername(System.getenv().getOrDefault("MYSQL_USERNAME", "root"));
        dataSource.setPassword(System.getenv().getOrDefault("MYSQL_PASSWORD", "123"));
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.afterPropertiesSet();
        SqlSessionFactory sqlSessionFactory = factoryBean.getObject();
        sqlSessionFactory.getConfiguration().addMapper(GrowthMapper.class);
        GrowthMapper growthMapper = new SqlSessionTemplate(sqlSessionFactory).getMapper(GrowthMapper.class);
        long userId = 199990001L;
        String eventId = UUID.randomUUID().toString();
        LocalDateTime occurredAt = LocalDateTime.of(2026, 11, 1, 0, 0);
        new TransactionTemplate(new DataSourceTransactionManager(dataSource)).executeWithoutResult(status -> {
            growthMapper.insertFlow(new LearningCompletedEvent(eventId, "LEARNING_COMPLETED", userId,
                    1L, 1L, 1L, occurredAt.toString(), "test"), 10, occurredAt);
            growthMapper.incrementAccount(userId, 10);
            assertTrue(growthMapper.selectTotalPoints().stream()
                    .anyMatch(points -> points.equals(new UserPoints(userId, 10))));
            assertTrue(growthMapper.selectMonthlyPoints("2026-11").stream()
                    .anyMatch(points -> points.equals(new UserPoints(userId, 10))));
            assertTrue(growthMapper.selectSeasonPoints("2026-11").stream()
                    .anyMatch(points -> points.equals(new UserPoints(userId, 10))));
            status.setRollbackOnly();
        });
    }
}
