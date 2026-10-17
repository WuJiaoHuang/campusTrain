package com.campustrain.trainingservice.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.campustrain.trainingservice.vo.TrainingInstanceVO;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.server.WebServer;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.core.env.Environment;

class TrainingInstanceInfoTest {

    @Test
    void readsServiceNameAndActualListeningPort() {
        Environment environment = mock(Environment.class);
        WebServerApplicationContext context = mock(WebServerApplicationContext.class);
        WebServer webServer = mock(WebServer.class);
        when(environment.getRequiredProperty("spring.application.name")).thenReturn("training-service");
        when(context.getWebServer()).thenReturn(webServer);
        when(webServer.getPort()).thenReturn(8085);

        TrainingInstanceVO instance = new TrainingInstanceInfo(environment, context).current();
        assertEquals("training-service", instance.serviceName());
        assertEquals(8085, instance.port());
        assertEquals("training-service:8085", instance.instanceId());
    }
}
