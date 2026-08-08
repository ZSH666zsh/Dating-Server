package com.dating.im.exception;

import lombok.Getter;

/** im-service 业务异常。code 对应 gRPC 业务码（5002~5004 等）。 */
@Getter
public class BizException extends RuntimeException {
    private final int code;
    public BizException(int code, String message) { super(message); this.code = code; }
}
