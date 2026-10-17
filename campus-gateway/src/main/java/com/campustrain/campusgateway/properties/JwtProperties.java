package com.campustrain.campusgateway.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

//把application.yml里的jwt配置读入Java代码中
@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    private String secret;
}
