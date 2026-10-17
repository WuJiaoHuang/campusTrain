package com.campustrain.trainingservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import com.campustrain.trainingservice.config.TrainingFeatureProperties;
import com.campustrain.trainingservice.config.TrainingInstanceInfo;
import com.campustrain.trainingservice.dto.TrainingCreateDTO;
import com.campustrain.trainingservice.exception.BusinessException;
import com.campustrain.trainingservice.mapper.ChapterMapper;
import com.campustrain.trainingservice.mapper.CourseMapper;
import com.campustrain.trainingservice.mapper.LessonMapper;
import com.campustrain.trainingservice.service.impl.TrainingServiceImpl;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class TrainingFeatureTest {

    @Test
    void createChecksCurrentFeatureValueBeforeOtherValidation() {
        TrainingFeatureProperties properties = new TrainingFeatureProperties();
        TrainingServiceImpl service = new TrainingServiceImpl(
                mock(CourseMapper.class), mock(ChapterMapper.class), mock(LessonMapper.class), properties,
                mock(TrainingInstanceInfo.class));
        TrainingCreateDTO dto = new TrainingCreateDTO();
        dto.setEnrollStartTime(LocalDateTime.of(2026, 1, 2, 0, 0));
        dto.setEnrollEndTime(LocalDateTime.of(2026, 1, 1, 0, 0));

        properties.setAllowCreate(false);
        BusinessException disabled = assertThrows(BusinessException.class, () -> service.createTraining(dto));
        assertEquals("当前已关闭培训创建功能", disabled.getMessage());

        properties.setAllowCreate(true);
        BusinessException validation = assertThrows(BusinessException.class, () -> service.createTraining(dto));
        assertEquals("报名结束时间不能早于报名开始时间", validation.getMessage());
    }
}
