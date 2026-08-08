package com.dating.user.exception;

/** 业务异常基类，与 post-service 一致 */
public class BizException extends RuntimeException {
    private final int code;  // 4101=用户不存在 4102=封禁 4103=昵称空 ...

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BizException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public int getCode() { return code; }
}
