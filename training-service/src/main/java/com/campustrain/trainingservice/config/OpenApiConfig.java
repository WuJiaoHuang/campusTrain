package com.campustrain.trainingservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI trainingServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("CampusTrain Training Service API")
                        .description("CampusTrain 培训项目服务接口文档")
                        .version("v1.0"));
    }
}
