package com.campustrain.authservice.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.campustrain.authservice.common.Result;
import com.campustrain.authservice.context.TraceIdContext;
import com.campustrain.authservice.dto.LoginDTO;
import com.campustrain.authservice.service.AuthService;
import com.campustrain.authservice.vo.LoginVO;
import com.campustrain.authservice.vo.TokenInfoVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
@Tag(name = "认证管理")
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "用户登录")
    @PostMapping("/login")
    @SentinelResource(value = "auth-login", blockHandler = "handleLoginBlock")
    public ResponseEntity<Result<LoginVO>> login(@Valid @RequestBody LoginDTO dto) {
        return ResponseEntity.ok(Result.success(authService.login(dto)));
    }

    public ResponseEntity<Result<LoginVO>> handleLoginBlock(LoginDTO dto, BlockException e) {
        String username = dto == null ? null : dto.getUsername();
        log.warn("Sentinel blocked request, traceId={}, resource=auth-login, username={}, blockType={}",
                TraceIdContext.get(), username, e.getClass().getSimpleName());
        log.debug("Sentinel block detail, resource=auth-login, username={}", username, e);
        if (e instanceof FlowException) {
            return ResponseEntity.status(429).body(Result.fail(429, "登录请求过于频繁，请稍后重试"));
        }
        return ResponseEntity.status(503).body(Result.fail(503, "认证服务暂时不可用，请稍后重试"));
    }

    @Operation(summary = "解析Token")
    @GetMapping("/get/token-info")
    public Result<TokenInfoVO> getTokenInfo(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return Result.success(authService.getTokenInfo(authorization));
    }
}
