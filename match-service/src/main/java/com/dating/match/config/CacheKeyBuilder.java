package com.dating.match.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Redis key 前缀构建器。
 * key 格式：{prefix}:match:{domain}:{id}
 * 对应 match-service-prd-tech.md §7.3
 */
@Component
@RequiredArgsConstructor
public class CacheKeyBuilder {

    @Value("${app.cache.key-prefix:zhaoshihang}")
    private String prefix;

    private String key(String domain, String id) {
        return prefix + ":match:" + domain + ":" + id;
    }

    /** 日配额 HASH：zhaoshihang:match:quota:{userId}:{yyyymmdd} */
    public String quota(Long userId, String yyyymmdd) {
        return key("quota", userId + ":" + yyyymmdd);
    }

    /** 推荐队列 LIST：zhaoshihang:match:feed:{userId} */
    public String feed(Long userId) {
        return key("feed", String.valueOf(userId));
    }

    /** 已 swipe SET：zhaoshihang:match:swiped:{userId} */
    public String swiped(Long userId) {
        return key("swiped", String.valueOf(userId));
    }

    /** 偏好画像 HASH：zhaoshihang:match:pref:{userId} */
    public String pref(Long userId) {
        return key("pref", String.valueOf(userId));
    }

    /** 锁前缀：zhaoshihang:lock:match: */
    public String lockPrefix() {
        return prefix + ":lock:match:";
    }

    /** DH 计划游标 */
    public String dhPlanCursor(String type) {
        return key("dh_plan:cursor", type);
    }

    /** DH 计划 cooldown：zhaoshihang:match:dh_plan:cooldown:{userId} */
    public String dhPlanCooldown(Long userId) {
        return key("dh_plan:cooldown", String.valueOf(userId));
    }

    /** DH 计划 lastScene：zhaoshihang:match:dh_plan:last_scene:{userId} */
    public String dhPlanLastScene(Long userId) {
        return key("dh_plan:last_scene", String.valueOf(userId));
    }

    /** 互动未读计数：zhaoshihang:match:notif:unread:{userId}:{like|visit} */
    public String notifUnread(Long userId, String type) {
        return key("notif:unread", userId + ":" + type);
    }

    /** 互动通知列表（最近事件 ZSet）：zhaoshihang:match:notif:list:{userId} */
    public String notifList(Long userId) {
        return key("notif:list", String.valueOf(userId));
    }
}
