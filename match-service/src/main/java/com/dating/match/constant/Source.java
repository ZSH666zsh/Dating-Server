package com.dating.match.constant;

/**
 * match.source 枚举值。
 * 对应 match-service-prd-tech.md §5.1 匹配矩阵。
 */
public class Source {

    /** 首页划卡常规匹配（BH互划 / DH延迟回调） */
    public static final String SWIPE_MATCH = "SWIPE_MATCH";

    /** Super Hi 硬匹配 */
    public static final String SWIPE_SUPER_HI = "SWIPE_SUPER_HI";

    private Source() {}
}
