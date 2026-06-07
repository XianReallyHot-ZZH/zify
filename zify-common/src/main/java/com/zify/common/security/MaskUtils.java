package com.zify.common.security;

import org.springframework.util.StringUtils;

/**
 * Utilities for masking sensitive values before display or logging.
 */
public final class MaskUtils {

    private static final String MASK = "***";

    private MaskUtils() {
    }

    public static String maskSecret(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= 4) {
            return MASK;
        }
        if (trimmed.length() <= 8) {
            return trimmed.charAt(0) + MASK + trimmed.charAt(trimmed.length() - 1);
        }
        return trimmed.substring(0, 4) + MASK + trimmed.substring(trimmed.length() - 4);
    }

    public static String maskApiKey(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String trimmed = value.trim();
        int dashIndex = trimmed.indexOf('-');
        if (dashIndex > 0 && dashIndex + 2 < trimmed.length()) {
            String prefix = trimmed.substring(0, Math.min(dashIndex + 2, trimmed.length()));
            String suffix = trimmed.substring(Math.max(prefix.length(), trimmed.length() - 4));
            return prefix + MASK + suffix;
        }
        return maskSecret(trimmed);
    }
}
