package com.dating.user.constant;

/** 错误码枚举（user-service 专用） */
public class ErrorCode {
    public static final int OK = 0;
    public static final int INTERNAL_ERROR = 5000;

    // 用户 4100~4199
    public static final int USER_NOT_FOUND = 4101;
    public static final int USER_BANNED = 4102;
    public static final int NICKNAME_EMPTY = 4103;
    public static final int NICKNAME_TOO_LONG = 4104;
    public static final int GENDER_INVALID = 4105;
    public static final int BIRTHDAY_INVALID = 4106;
    public static final int PHONE_INVALID = 4107;
    public static final int PHONE_ALREADY_BOUND = 4108;
    public static final int AVATAR_KEY_INVALID = 4109;
    public static final int USER_ALREADY_EXISTS = 4110;

    // 权限
    public static final int FORBIDDEN = 4030;
}
