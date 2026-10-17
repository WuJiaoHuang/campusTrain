package com.campustrain.trainingservice.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.campustrain.trainingservice.config.TrainingFaultProperties;
import com.campustrain.trainingservice.config.TrainingInstanceInfo;
import com.campustrain.trainingservice.controller.TrainingController;
import com.campustrain.trainingservice.vo.TrainingVO;
import org.junit.jupiter.api.Test;

class Stage10Test {
    @Test
    void faultDefaultsAreDisabled() {
        TrainingFaultProperties properties = new TrainingFaultProperties();
        assertFalse(properties.isDelayEnabled());
        assertFalse(properties.isExceptionEnabled());
        assertEquals(1500, properties.getDelayMs());
    }

    @Test
    void normalAndBlockedResponsesAreDistinct() {
        TrainingService service = mock(TrainingService.class);
        TrainingController controller = new TrainingController(service, mock(TrainingInstanceInfo.class));
        TrainingVO value = new TrainingVO();
        when(service.getTrainingDetail(1L)).thenReturn(value);
        assertSame(value, controller.getTrainingDetail(1L).getBody().getData());
        assertEquals(429, controller.handleTrainingDetailBlock(1L, new FlowException("flow")).getStatusCode().value());
        assertEquals(503, controller.handleTrainingDetailBlock(1L, new DegradeException("degrade")).getStatusCode().value());
    }
}
