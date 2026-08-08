package com.dating.match.constant;

/**
 * 错误码定义。
 * 参考 match-service-prd-tech.md §8.1。
 */
public class ErrorCode {

    // ─── 通用 ───
    public static final int OK = 0;
    public static final int INTERNAL_ERROR = 5000;

    // ─── 划卡 ───
    /** 右划配额耗尽 */
    public static final int QUOTA_RIGHT_SWIPE_EXCEEDED = 2001;
    /** 卡片配额耗尽 */
    public static final int QUOTA_CARDS_EXCEEDED = 2002;
    /** Super Hi 配额耗尽 */
    public static final int QUOTA_SUPER_HI_EXCEEDED = 2003;
    /** 并发划卡冲突 */
    public static final int CONCURRENT_SWIPE = 2004;
    /** 重复划卡（幂等返回上次结果） */
    public static final int SWIPE_DUPLICATE = 2005;

    // ─── 用户 ───
    /** 用户不存在 */
    public static final int USER_NOT_FOUND = 4001;
    /** 目标用户已注销 */
    public static final int TARGET_DELETED = 4002;

    // ─── 匹配 ───
    /** 重复匹配 */
    public static final int MATCH_DUPLICATE = 3001;

    // ─── 配额 ───
    /** 金币不足 */
    public static final int INSUFFICIENT_COINS = 6001;

    // ─── 参数 ───
    public static final int INVALID_PARAM = 4100;

    private ErrorCode() {}
}
