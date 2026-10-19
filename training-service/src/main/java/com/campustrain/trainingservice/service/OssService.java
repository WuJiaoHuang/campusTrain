package com.campustrain.trainingservice.service;

import com.aliyun.oss.model.ObjectMetadata;
import java.time.Instant;

public interface OssService {
    String generateUploadUrl(String objectKey, String contentType, Instant expiry);
    String generatePlayUrl(String objectKey, Instant expiry);
    boolean doesObjectExist(String objectKey);
    ObjectMetadata getObjectMetadata(String objectKey);
    void deleteObject(String objectKey);
}
