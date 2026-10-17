package com.campustrain.enrollmentservice.client;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class Stage10FallbackTest {
    @Test
    void fallbacksReturnFailureWithoutInventedData() {
        var training = new TrainingClientFallbackFactory().create(new RuntimeException("test")).getTrainingById(1L);
        var user = new UserClientFallbackFactory().create(new RuntimeException("test")).getUserById(1L);
        assertEquals(503, training.getCode());
        assertNull(training.getData());
        assertEquals(503, user.getCode());
        assertNull(user.getData());
    }
}
