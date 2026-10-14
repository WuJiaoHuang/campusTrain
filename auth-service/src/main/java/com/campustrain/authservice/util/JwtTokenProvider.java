package com.campustrain.authservice.util;

import com.campustrain.authservice.properties.JwtProperties;
import com.campustrain.authservice.security.LoginUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final JwtProperties jwtProperties;

    public String generateToken(LoginUser loginUser) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + jwtProperties.getExpiration());
        return Jwts.builder()
                .subject(loginUser.getUsername())
                .claim("userId", loginUser.getUserId())
                .claim("role", loginUser.getRole())
                .issuedAt(now)
                .expiration(expiration)
                .signWith(getSigningKey())
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean validateToken(String token) {
        parseToken(token);
        return true;
    }

    public boolean isExpired(String token) {
        return parseToken(token).getExpiration().before(new Date());
    }

    public Long getUserId(String token) {
        Object userId = parseToken(token).get("userId");
        if (userId instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(String.valueOf(userId));
    }

    public String getUsername(String token) {
        return parseToken(token).getSubject();
    }

    public String getRole(String token) {
        return parseToken(token).get("role", String.class);
    }

    public Long getExpiresInSeconds() {
        return jwtProperties.getExpiration() / 1000;
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            log.warn("jwt secret length is less than 32 bytes");
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
