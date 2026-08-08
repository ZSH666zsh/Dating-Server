package com.dating.gateway.constant;

/**
 * 错误码定义。
 * 对应 mobile-gateway-design.md §5.6 错误约定。
 */
public class ErrorCode {

    // ─── 通用 ───
    public static final int OK = 0;
    public static final int INTERNAL_ERROR = 500;

    // ─── Token（105xx） ───
    public static final int TOKEN_INVALID = 10501;
    public static final int TOKEN_EXPIRED = 10502;
    public static final int TOKEN_REVOKED = 10503;
    public static final int REFRESH_TOKEN_REUSED = 10504;
    public static final int REFRESH_TOKEN_DEVICE_MISMATCH = 10505;

    // ─── 短信 / 三方（106xx） ───
    public static final int SMS_CODE_INVALID = 10601;
    public static final int SMS_CODE_EXPIRED = 10602;
    public static final int THIRD_PARTY_TOKEN_INVALID = 10603;

    // ─── 用户（网关层） ───
    public static final int USER_BANNED = 10901;
    public static final int UPSTREAM_UNAVAILABLE = 10902;
    public static final int RATE_LIMITED = 429;

    // ─── 参数 ───
    public static final int INVALID_PARAM = 400;

    private ErrorCode() {}
}
