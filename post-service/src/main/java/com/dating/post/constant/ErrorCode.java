package com.dating.post.constant;

/**
 * 错误码枚举（post-service 专用）
 */
public class ErrorCode {

    // ─── 通用 ───
    public static final int OK = 0;
    public static final int INTERNAL_ERROR = 5000;

    // ─── 帖子 4001~4029 ───
    public static final int CONTENT_EMPTY = 4001;
    public static final int CONTENT_TOO_LONG = 4002;
    public static final int IMAGE_COUNT_EXCEEDED = 4003;
    public static final int IMAGE_KEY_EMPTY = 4004;
    public static final int POST_NOT_FOUND = 4005;

    // ─── 评论 4030~4059 ───
    public static final int COMMENT_NOT_FOUND = 4006;
    public static final int COMMENT_CONTENT_EMPTY = 4007;
    public static final int COMMENT_CONTENT_TOO_LONG = 4008;

    // ─── 权限 ───
    public static final int FORBIDDEN = 4030;
    public static final int UNAUTHORIZED = 4031;
}
