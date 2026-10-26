package com.campustrain.learningservice;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RequestIdentity extends OncePerRequestFilter {
    private static final ThreadLocal<Identity> CURRENT = new ThreadLocal<>();
    public record Identity(Long userId, String role, String username, String traceId) {}

    public static Identity current() { return CURRENT.get(); }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Long userId = null;
        try {
            String header = request.getHeader("X-User-Id");
            if (header != null) userId = Long.valueOf(header);
        } catch (NumberFormatException ignored) {
            // Invalid identity is treated as missing.
        }
        CURRENT.set(new Identity(userId, request.getHeader("X-User-Role"),
                request.getHeader("X-Username"), request.getHeader("X-Trace-Id")));
        try { chain.doFilter(request, response); } finally { CURRENT.remove(); }
    }
}
