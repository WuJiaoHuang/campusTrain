package com.campustrain.trainingservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.cloud.nacos.discovery.enabled=false")
class TrainingServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
