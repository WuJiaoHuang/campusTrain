package com.campustrain.enrollmentservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI enrollmentServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("CampusTrain Enrollment Service API")
                        .description("CampusTrain 报名服务接口文档")
                        .version("v1.0"));
    }
}
