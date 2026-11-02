package com.campustrain.trainingservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.campustrain.trainingservice.config.OssFaultProperties;
import com.campustrain.trainingservice.config.OssProperties;
import com.campustrain.trainingservice.service.impl.OssServiceImpl;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class OssFaultInjectionTest {

    @Test
    void faultInjectionDefaultsOff() {
        OssFaultProperties properties = new OssFaultProperties();
        assertFalse(properties.isEnabled());
        assertFalse(properties.isPlayUrlException());
    }

    @Test
    void enabledPlayUrlFaultThrowsSystemExceptionBeforeOssCall() {
        OssProperties ossProperties = new OssProperties();
        ossProperties.setAccessKeyId("test-key");
        ossProperties.setAccessKeySecret("test-secret");
        OssFaultProperties faultProperties = new OssFaultProperties();
        faultProperties.setEnabled(true);
        faultProperties.setPlayUrlException(true);
        OssServiceImpl service = new OssServiceImpl(ossProperties, faultProperties);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.generatePlayUrl("campustrain/videos/test.mp4", Instant.now().plusSeconds(60)));

        assertEquals("Injected OSS play URL system fault", exception.getMessage());
    }
}
