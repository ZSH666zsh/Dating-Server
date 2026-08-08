package com.dating.im.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ResponseEntity<Map<String, Object>> handleBiz(BizException e) {
        log.warn("Biz: code={} msg={}", e.getCode(), e.getMessage());
        return ResponseEntity.ok(Map.of("code", e.getCode(), "message", e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleEx(Exception e) {
        log.error("Unhandled", e);
        return ResponseEntity.internalServerError().body(Map.of("code", 500, "message", "服务器内部错误"));
    }
}
