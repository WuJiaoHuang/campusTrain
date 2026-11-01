package com.campustrain.authservice.service.impl;

import com.campustrain.authservice.dto.LoginDTO;
import com.campustrain.authservice.exception.BusinessException;
import com.campustrain.authservice.security.LoginUser;
import com.campustrain.authservice.service.AuthService;
import com.campustrain.authservice.util.JwtTokenProvider;
import com.campustrain.authservice.vo.LoginVO;
import com.campustrain.authservice.vo.TokenInfoVO;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public LoginVO login(LoginDTO dto) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(dto.getUsername(), dto.getPassword())
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);

            LoginUser loginUser = (LoginUser) authentication.getPrincipal();
            String token = jwtTokenProvider.generateToken(loginUser);

            LoginVO vo = new LoginVO();
            vo.setToken(token);
            vo.setTokenType("Bearer");
            vo.setExpiresIn(jwtTokenProvider.getExpiresInSeconds());
            vo.setUserId(loginUser.getUserId());
            vo.setUsername(loginUser.getUsername());
            vo.setRole(loginUser.getRole());

            log.info(
                    "login success, userId={}, username={}, role={}",
                    loginUser.getUserId(),
                    loginUser.getUsername(),
                    loginUser.getRole()
            );
            return vo;
        } catch (AuthenticationException e) {
            log.warn("login failed, username={}", dto.getUsername());
            throw new BusinessException(401, "用户名或密码错误");
        }
    }

    @Override
    public TokenInfoVO getTokenInfo(String authorization) {
        String token = resolveBearerToken(authorization);
        Claims claims = jwtTokenProvider.parseToken(token);

        TokenInfoVO vo = new TokenInfoVO();
        Object userId = claims.get("userId");
        if (userId instanceof Number number) {
            vo.setUserId(number.longValue());
        } else {
            vo.setUserId(Long.valueOf(String.valueOf(userId)));
        }
        vo.setUsername(claims.getSubject());
        vo.setRole(claims.get("role", String.class));
        return vo;
    }

    private String resolveBearerToken(String authorization) {
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            throw new BusinessException(401, "Token缺失");
        }
        return authorization.substring(7);
    }
}
