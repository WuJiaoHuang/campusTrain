package com.campustrain.enrollmentservice.filter;

import com.campustrain.enrollmentservice.context.UserContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@Component
public class UserContextFilter extends OncePerRequestFilter {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_ROLE_HEADER = "X-User-Role";
    private static final String USERNAME_HEADER = "X-Username";
    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            String userIdHeader = request.getHeader(USER_ID_HEADER);
            if (StringUtils.hasText(userIdHeader)) {
                try {
                    UserContext.set(
                            Long.valueOf(userIdHeader),
                            request.getHeader(USER_ROLE_HEADER),
                            request.getHeader(USERNAME_HEADER),
                            request.getHeader(TRACE_ID_HEADER)
                    );
                } catch (NumberFormatException e) {
                    log.warn("invalid user context header, traceId={}", request.getHeader(TRACE_ID_HEADER));
                    UserContext.clear();
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            UserContext.clear();
        }
    }
}
