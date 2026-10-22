package com.campustrain.enrollmentservice.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.campustrain.enrollmentservice.mq.MqConstants;
import com.campustrain.enrollmentservice.mq.event.EnrollmentSuccessEvent;
import com.campustrain.enrollmentservice.mq.event.LearningCompletedEvent;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;

class Stage15MqContractTest {

    @Test
    void eventContractsCarryTheRequiredIdentifiers() {
        EnrollmentSuccessEvent enrollment = new EnrollmentSuccessEvent();
        enrollment.setEventId(UUID.randomUUID().toString());
        enrollment.setUserId(1L); enrollment.setTrainingId(2L); enrollment.setEnrollmentId(3L);
        LearningCompletedEvent learning = new LearningCompletedEvent();
        learning.setEventId(UUID.randomUUID().toString());
        learning.setUserId(1L); learning.setTrainingId(2L); learning.setLessonId(4L);
        assertEquals("ENROLLMENT_SUCCESS", enrollment.getEventType());
        assertEquals("LEARNING_COMPLETED", learning.getEventType());
        assertTrue(enrollment.getEventId().length() >= 32);
        assertTrue(learning.getEventId().length() >= 32);
    }

    @Test
    void businessQueuesAreDurableAndDeadLetterEnabled() {
        RabbitMqConfig config = new RabbitMqConfig();
        Queue enrollment = config.enrollmentSuccessQueue();
        Queue learning = config.learningCompletedQueue();
        assertTrue(enrollment.isDurable());
        assertTrue(learning.isDurable());
        assertEquals(MqConstants.DLX, enrollment.getArguments().get("x-dead-letter-exchange"));
        assertEquals(MqConstants.DLX, learning.getArguments().get("x-dead-letter-exchange"));
    }
}
