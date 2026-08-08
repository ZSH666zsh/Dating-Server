package com.dating.im.service;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 站外联系方式检测器。对应 im-service-design.md §11。
 *
 * <p>命中规则时返回规则名（instagram / facebook / whatsapp / telegram / us_phone），否则 null。
 */
@Component
public class ContactInfoDetector {

    private static final Pattern INSTAGRAM = Pattern.compile(
            "instagram\\.com/[\\w.]+|\\big:\\s*@?\\w+", Pattern.CASE_INSENSITIVE);
    private static final Pattern FACEBOOK = Pattern.compile(
            "facebook\\.com/[\\w.]+|\\bfb:\\s*@?\\w+", Pattern.CASE_INSENSITIVE);
    private static final Pattern WHATSAPP = Pattern.compile(
            "wa\\.me/[\\w]+|\\bwhatsapp", Pattern.CASE_INSENSITIVE);
    private static final Pattern TELEGRAM = Pattern.compile(
            "t\\.me/[\\w]+|\\btg:\\s*@?\\w+", Pattern.CASE_INSENSITIVE);
    private static final Pattern US_PHONE = Pattern.compile(
            "(?<!\\d)(?:\\+?1)?[-.\\s]?\\(?\\d{3}\\)?[-.\\s]?\\d{3}[-.\\s]?\\d{4}(?!\\d)");

    /** 检测文本。命中返回规则名，否则 null。 */
    public String detect(String content) {
        if (content == null || content.isBlank()) return null;

        if (INSTAGRAM.matcher(content).find()) return "instagram";
        if (FACEBOOK.matcher(content).find()) return "facebook";
        if (WHATSAPP.matcher(content).find()) return "whatsapp";
        if (TELEGRAM.matcher(content).find()) return "telegram";
        if (US_PHONE.matcher(content).find()) return "us_phone";

        return null;
    }
}
