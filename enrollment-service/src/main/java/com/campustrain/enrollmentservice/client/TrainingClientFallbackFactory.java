package com.campustrain.enrollmentservice.client;

import com.campustrain.enrollmentservice.context.UserContext;
import com.campustrain.enrollmentservice.remote.RemoteResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TrainingClientFallbackFactory implements FallbackFactory<TrainingClient> {
    @Override
    public TrainingClient create(Throwable cause) {
        return id -> {
            log.error("training Feign fallback, traceId={}, trainingId={}", UserContext.getTraceId(), id, cause);
            return RemoteResult.unavailable("培训服务暂时不可用");
        };
    }
}
