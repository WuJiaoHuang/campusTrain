package com.campustrain.enrollmentservice.config;

import com.campustrain.enrollmentservice.context.UserContext;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class TraceIdFeignRequestInterceptor implements RequestInterceptor {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    public void apply(RequestTemplate template) {
        String traceId = UserContext.getTraceId();
        if (StringUtils.hasText(traceId)) {
            template.header(TRACE_ID_HEADER, traceId);
        }
    }
}
