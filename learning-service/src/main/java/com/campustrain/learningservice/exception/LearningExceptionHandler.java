package com.campustrain.learningservice.exception;

import com.campustrain.learningservice.context.RequestIdentityContext;
import com.campustrain.learningservice.vo.ApiResult;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class LearningExceptionHandler {
    @ExceptionHandler(LearningFailure.class)
    ResponseEntity<ApiResult<Void>> business(LearningFailure exception) {
        return ResponseEntity.status(exception.getCode())
                .body(new ApiResult<>(exception.getCode(), exception.getMessage(), null));
    }

    @ExceptionHandler({FeignException.class, DataAccessException.class})
    ResponseEntity<ApiResult<Void>> unavailable(Exception exception) {
        log.error("learning dependency unavailable, identity={}", RequestIdentityContext.current(), exception);
        return ResponseEntity.status(503).body(new ApiResult<>(503, "学习服务依赖暂时不可用", null));
    }
}
