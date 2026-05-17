package com.agentpilot.common.security;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DataMasking {
    private static final Pattern CHINA_MOBILE = Pattern.compile("(?<!\\d)(1[3-9]\\d)(\\d{4})(\\d{4})(?!\\d)");
    private static final Pattern EMAIL = Pattern.compile("([A-Za-z0-9._%+-])([A-Za-z0-9._%+-]*)(@[A-Za-z0-9.-]+\\.[A-Za-z]{2,})");

    private DataMasking() {
    }

    public static String maskMobile(String mobile) {
        if (mobile == null || mobile.length() < 7) {
            return mobile;
        }
        return mobile.substring(0, 3) + "****" + mobile.substring(mobile.length() - 4);
    }

    public static String maskSensitiveText(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        String masked = maskMobilesInText(text);
        return maskEmailsInText(masked);
    }

    private static String maskMobilesInText(String text) {
        Matcher matcher = CHINA_MOBILE.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, matcher.group(1) + "****" + matcher.group(3));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private static String maskEmailsInText(String text) {
        Matcher matcher = EMAIL.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, matcher.group(1) + "***" + matcher.group(3));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }
}
