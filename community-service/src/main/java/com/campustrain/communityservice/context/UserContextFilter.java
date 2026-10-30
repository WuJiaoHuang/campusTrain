package com.campustrain.communityservice.context;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class UserContextFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Long userId = null;
        try {
            String raw = request.getHeader("X-User-Id");
            if (raw != null) userId = Long.valueOf(raw);
        } catch (NumberFormatException ignored) { }
        UserContext.set(new UserIdentity(userId, request.getHeader("X-User-Role"),
                request.getHeader("X-Username"), request.getHeader("X-Trace-Id")));
        try { chain.doFilter(request, response); }
        finally { UserContext.clear(); }
    }
}
