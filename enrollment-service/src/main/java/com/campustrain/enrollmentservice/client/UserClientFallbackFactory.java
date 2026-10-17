package com.campustrain.enrollmentservice.client;

import com.campustrain.enrollmentservice.context.UserContext;
import com.campustrain.enrollmentservice.remote.RemoteResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class UserClientFallbackFactory implements FallbackFactory<UserClient> {
    @Override
    public UserClient create(Throwable cause) {
        return id -> {
            log.error("user Feign fallback, traceId={}, userId={}", UserContext.getTraceId(), id, cause);
            return RemoteResult.unavailable("用户服务暂时不可用");
        };
    }
}
