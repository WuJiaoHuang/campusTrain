package com.campustrain.trainingservice.config;

import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "campustrain.oss")
public class OssProperties {
    private String endpoint = "https://oss-cn-beijing.aliyuncs.com";
    private String region = "cn-beijing";
    private String bucket = "java-ai-wujiaohuang";
    private String accessKeyId;
    @ToString.Exclude
    private String accessKeySecret;
    private long uploadExpireSeconds = 900;
    private long playExpireSeconds = 600;
    private long maxVideoSize = 524288000;
}
