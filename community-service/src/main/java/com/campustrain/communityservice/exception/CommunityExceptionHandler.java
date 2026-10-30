package com.campustrain.communityservice.exception;

import com.campustrain.communityservice.context.UserContext;
import com.campustrain.communityservice.vo.ApiResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class CommunityExceptionHandler {
    @ExceptionHandler(CommunityException.class)
    public ResponseEntity<ApiResult<Void>> business(CommunityException ex) {
        return ResponseEntity.status(ex.status()).body(ApiResult.fail(ex.status(), ex.getMessage()));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResult<Void>> validation(MethodArgumentNotValidException ex) {
        return ResponseEntity.badRequest().body(ApiResult.fail(400, "请求参数不合法"));
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResult<Void>> unknown(Exception ex) {
        log.error("community request failed, traceId={}", UserContext.traceId(), ex);
        return ResponseEntity.internalServerError().body(ApiResult.fail(500, "服务暂时不可用"));
    }
}
