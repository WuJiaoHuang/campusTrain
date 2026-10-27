package com.campustrain.learningservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI learningServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("CampusTrain Learning Service API")
                        .description("CampusTrain 学习进度服务接口文档")
                        .version("v1.0"));
    }
}
