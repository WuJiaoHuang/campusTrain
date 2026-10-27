package com.campustrain.learningservice.context;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RequestIdentity extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Long userId = null;
        try {
            String header = request.getHeader("X-User-Id");
            if (header != null) {
                userId = Long.valueOf(header);
            }
        } catch (NumberFormatException ignored) {
            // Invalid identity is treated as missing.
        }
        RequestIdentityContext.set(new UserIdentity(userId, request.getHeader("X-User-Role"),
                request.getHeader("X-Username"), request.getHeader("X-Trace-Id")));
        try {
            chain.doFilter(request, response);
        } finally {
            RequestIdentityContext.clear();
        }
    }
}
