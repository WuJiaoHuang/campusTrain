package com.campustrain.authservice.service.impl;

import com.campustrain.authservice.dto.LoginDTO;
import com.campustrain.authservice.exception.BusinessException;
import com.campustrain.authservice.security.LoginUser;
import com.campustrain.authservice.service.AuthService;
import com.campustrain.authservice.util.JwtTokenProvider;
import com.campustrain.authservice.vo.LoginVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

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
}
