package com.campustrain.authservice.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.campustrain.authservice.context.TraceIdContext;
import jakarta.servlet.FilterChain;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TraceIdFilterTest {

    private final TraceIdFilter traceIdFilter = new TraceIdFilter();

    @AfterEach
    void clearContext() {
        TraceIdContext.clear();
    }

    @Test
    void keepsTraceIdFromRequestHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Trace-Id", "trace-from-gateway");
        AtomicReference<String> traceIdInChain = new AtomicReference<>();

        traceIdFilter.doFilter(request, new MockHttpServletResponse(), captureTraceId(traceIdInChain));

        assertEquals("trace-from-gateway", traceIdInChain.get());
        assertNull(TraceIdContext.get());
    }

    @Test
    void generatesTraceIdWhenHeaderMissing() throws Exception {
        AtomicReference<String> traceIdInChain = new AtomicReference<>();

        traceIdFilter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), captureTraceId(traceIdInChain));

        assertNotNull(traceIdInChain.get());
        assertEquals(32, traceIdInChain.get().length());
        assertNull(TraceIdContext.get());
    }

    private FilterChain captureTraceId(AtomicReference<String> traceIdInChain) {
        return (request, response) -> traceIdInChain.set(TraceIdContext.get());
    }
}
