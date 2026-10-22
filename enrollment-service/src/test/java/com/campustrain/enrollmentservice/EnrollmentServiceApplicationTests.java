package com.campustrain.enrollmentservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
class EnrollmentServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
