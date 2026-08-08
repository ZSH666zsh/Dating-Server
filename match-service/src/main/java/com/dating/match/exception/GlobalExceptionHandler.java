package com.dating.match.exception;

import com.dating.match.constant.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * 全局异常处理 —— REST 统一返回 {code, message, data}，HTTP 始终 200。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ResponseEntity<Map<String, Object>> handleBizException(BizException e) {
        log.warn("BizException: code={}, message={}", e.getCode(), e.getMessage());
        Map<String, Object> result = new HashMap<>();
        result.put("code", e.getCode());
        result.put("message", e.getMessage());
        result.put("data", null);
        return ResponseEntity.ok(result);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception e) {
        log.error("Unhandled exception", e);
        Map<String, Object> result = new HashMap<>();
        result.put("code", ErrorCode.INTERNAL_ERROR);
        result.put("message", "服务器内部错误");
        result.put("data", null);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
    }
}
