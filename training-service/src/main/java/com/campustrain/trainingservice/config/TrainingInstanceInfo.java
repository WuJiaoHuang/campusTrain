package com.campustrain.trainingservice.config;

import com.campustrain.trainingservice.vo.TrainingInstanceVO;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TrainingInstanceInfo {

    private final Environment environment;
    private final WebServerApplicationContext webServerContext;

    public TrainingInstanceVO current() {
        String serviceName = environment.getRequiredProperty("spring.application.name");
        int port = webServerContext.getWebServer().getPort();
        return new TrainingInstanceVO(serviceName, port, serviceName + ":" + port);
    }
}
