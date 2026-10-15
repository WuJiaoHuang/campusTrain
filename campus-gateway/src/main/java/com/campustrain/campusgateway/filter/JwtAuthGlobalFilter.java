package com.campustrain.campusgateway.filter;

import com.campustrain.campusgateway.util.JwtTokenParser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {

    private static final String AUTHORIZATION = HttpHeaders.AUTHORIZATION;
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_ROLE_HEADER = "X-User-Role";
    private static final String USERNAME_HEADER = "X-Username";
    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    private final JwtTokenParser jwtTokenParser;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String traceId = resolveTraceId(request.getHeaders().getFirst(TRACE_ID_HEADER));
        String path = request.getURI().getPath();
        String targetService = resolveTargetService(path);

        ServerHttpRequest.Builder requestBuilder = request.mutate();
        requestBuilder.headers(headers -> {
            headers.remove(USER_ID_HEADER);
            headers.remove(USER_ROLE_HEADER);
            headers.remove(USERNAME_HEADER);
            headers.remove(TRACE_ID_HEADER);
            headers.set(TRACE_ID_HEADER, traceId);
        });
        exchange.getResponse().getHeaders().set(TRACE_ID_HEADER, traceId);

        if (isWhitelist(request.getMethod(), path)) {
            log.info(
                    "gateway request, traceId={}, method={}, path={}, userId=anonymous, targetService={}",
                    traceId,
                    request.getMethod(),
                    path,
                    targetService
            );
            return forward(exchange, requestBuilder, chain, traceId, null, path, targetService);
        }

        String authorization = request.getHeaders().getFirst(AUTHORIZATION);
        if (!StringUtils.hasText(authorization) || !authorization.startsWith(BEARER_PREFIX)) {
            return unauthorized(exchange, traceId, path, targetService, "missing bearer token");
        }

        try {
            Claims claims = jwtTokenParser.parse(authorization.substring(BEARER_PREFIX.length()));
            Long userId = getUserId(claims);
            String role = claims.get("role", String.class);
            String username = claims.getSubject();
            if (userId == null || !StringUtils.hasText(role) || !StringUtils.hasText(username)) {
                return unauthorized(exchange, traceId, path, targetService, "required jwt claims missing");
            }

            requestBuilder.headers(headers -> {
                headers.set(USER_ID_HEADER, String.valueOf(userId));
                headers.set(USER_ROLE_HEADER, role);
                headers.set(USERNAME_HEADER, username);
            });
            log.info(
                    "gateway request, traceId={}, method={}, path={}, userId={}, targetService={}",
                    traceId,
                    request.getMethod(),
                    path,
                    userId,
                    targetService
            );
            return forward(exchange, requestBuilder, chain, traceId, userId, path, targetService);
        } catch (JwtException | IllegalArgumentException e) {
            return unauthorized(exchange, traceId, path, targetService, e.getClass().getSimpleName());
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private Mono<Void> forward(
            ServerWebExchange exchange,
            ServerHttpRequest.Builder requestBuilder,
            GatewayFilterChain chain,
            String traceId,
            Long userId,
            String path,
            String targetService
    ) {
        ServerWebExchange mutatedExchange = exchange.mutate().request(requestBuilder.build()).build();
        return chain.filter(mutatedExchange)
                .doOnError(error -> log.error(
                        "gateway request failed, traceId={}, path={}, userId={}, targetService={}",
                        traceId,
                        path,
                        userId,
                        targetService,
                        error
                ));
    }

    private Mono<Void> unauthorized(
            ServerWebExchange exchange,
            String traceId,
            String path,
            String targetService,
            String reason
    ) {
        log.warn(
                "gateway authentication rejected, traceId={}, path={}, targetService={}, reason={}",
                traceId,
                path,
                targetService,
                reason
        );
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders().set(TRACE_ID_HEADER, traceId);
        byte[] bytes = "{\"code\":401,\"message\":\"未登录或Token无效\",\"data\":null}"
                .getBytes(StandardCharsets.UTF_8);
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
    }

    private boolean isWhitelist(HttpMethod method, String path) {
        return (HttpMethod.POST.equals(method) && "/auth/login".equals(path))
                || (HttpMethod.GET.equals(method) && "/actuator/health".equals(path));
    }

    private Long getUserId(Claims claims) {
        Object value = claims.get("userId");
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        return Long.valueOf(String.valueOf(value));
    }

    private String resolveTraceId(String requestedTraceId) {
        return StringUtils.hasText(requestedTraceId)
                ? requestedTraceId
                : UUID.randomUUID().toString().replace("-", "");
    }

    private String resolveTargetService(String path) {
        if ("/auth".equals(path) || path.startsWith("/auth/")) {
            return "auth-service";
        }
        if ("/users".equals(path) || path.startsWith("/users/")) {
            return "user-service";
        }
        if ("/trainings".equals(path) || path.startsWith("/trainings/")) {
            return "training-service";
        }
        if ("/enrollments".equals(path) || path.startsWith("/enrollments/")) {
            return "enrollment-service";
        }
        return "unmatched";
    }
}
