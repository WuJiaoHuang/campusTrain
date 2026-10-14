package com.campustrain.authservice.controller;

import com.campustrain.authservice.common.Result;
import com.campustrain.authservice.dto.LoginDTO;
import com.campustrain.authservice.exception.BusinessException;
import com.campustrain.authservice.service.AuthService;
import com.campustrain.authservice.util.JwtTokenProvider;
import com.campustrain.authservice.vo.LoginVO;
import com.campustrain.authservice.vo.TokenInfoVO;
import io.jsonwebtoken.Claims;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
@Tag(name = "认证管理")
public class AuthController {

    private final AuthService authService;
    private final JwtTokenProvider jwtTokenProvider;

    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return Result.success(authService.login(dto));
    }

    @Operation(summary = "解析Token")
    @GetMapping("/token-info")
    public Result<TokenInfoVO> tokenInfo(@RequestHeader(value = "Authorization", required = false) String authorization) {
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
        return Result.success(vo);
    }

    private String resolveBearerToken(String authorization) {
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            throw new BusinessException(401, "Token缺失");
        }
        return authorization.substring(7);
    }
}
