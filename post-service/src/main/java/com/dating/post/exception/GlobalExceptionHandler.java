package com.dating.post.exception;

import com.dating.post.constant.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * 全局异常处理
 * REST 统一返回 { code, message, data }，HTTP 状态码始终 200
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ResponseEntity<Map<String, Object>> handleBizException(BizException e) {
        log.warn("BizException: code={}, message={}", e.getCode(), e.getMessage());
        return ResponseEntity.ok(Map.of(
                "code", e.getCode(),
                "message", e.getMessage(),
                "data", null
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "code", ErrorCode.INTERNAL_ERROR,
                "message", "服务器内部错误"
        ));
    }
}
