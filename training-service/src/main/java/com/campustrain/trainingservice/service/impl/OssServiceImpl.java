package com.campustrain.trainingservice.service.impl;

import com.aliyun.oss.ClientBuilderConfiguration;
import com.aliyun.oss.HttpMethod;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.common.auth.DefaultCredentialProvider;
import com.aliyun.oss.common.comm.SignVersion;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.aliyun.oss.model.ObjectMetadata;
import com.campustrain.trainingservice.config.OssProperties;
import com.campustrain.trainingservice.service.OssService;
import java.time.Instant;
import java.util.Date;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class OssServiceImpl implements OssService {
    private final OssProperties properties;

    private <T> T withClient(Function<OSS, T> action) {
        if (!StringUtils.hasText(properties.getAccessKeyId()) || !StringUtils.hasText(properties.getAccessKeySecret())) {
            throw new IllegalStateException("OSS credentials are not configured");
        }
        ClientBuilderConfiguration config = new ClientBuilderConfiguration();
        config.setSignatureVersion(SignVersion.V4);
        OSS client = OSSClientBuilder.create().endpoint(properties.getEndpoint())
                .region(properties.getRegion())
                .credentialsProvider(new DefaultCredentialProvider(properties.getAccessKeyId(), properties.getAccessKeySecret()))
                .clientConfiguration(config).build();
        try {
            return action.apply(client);
        } finally {
            client.shutdown();
        }
    }

    private String sign(String key, String type, Instant expiry, HttpMethod method) {
        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(properties.getBucket(), key, method);
        request.setExpiration(Date.from(expiry));
        if (type != null) request.setContentType(type);
        return withClient(client -> client.generatePresignedUrl(request).toString());
    }

    @Override
    public String generateUploadUrl(String key, String type, Instant expiry) {
        return sign(key, type, expiry, HttpMethod.PUT);
    }

    @Override
    public String generatePlayUrl(String key, Instant expiry) {
        return sign(key, null, expiry, HttpMethod.GET);
    }

    @Override
    public boolean doesObjectExist(String key) {
        return withClient(client -> client.doesObjectExist(properties.getBucket(), key));
    }

    @Override
    public ObjectMetadata getObjectMetadata(String key) {
        return withClient(client -> client.getObjectMetadata(properties.getBucket(), key));
    }

    @Override
    public void deleteObject(String key) {
        withClient(client -> { client.deleteObject(properties.getBucket(), key); return null; });
    }
}
