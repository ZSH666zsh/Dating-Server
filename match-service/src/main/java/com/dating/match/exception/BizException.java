package com.dating.match.exception;

import lombok.Getter;

/**
 * 业务异常基类。
 * 统一携带 code + message，由 GlobalExceptionHandler 转为统一响应。
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }
}
