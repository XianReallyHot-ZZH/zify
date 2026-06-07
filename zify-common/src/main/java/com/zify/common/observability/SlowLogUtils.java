package com.zify.common.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;

/**
 * Utilities for logging slow operations.
 */
public final class SlowLogUtils {

    private static final Logger log = LoggerFactory.getLogger(SlowLogUtils.class);

    private SlowLogUtils() {
    }

    public static void logIfSlow(String event, long durationMs, long thresholdMs, Map<String, Object> context) {
        if (durationMs < thresholdMs) {
            return;
        }
        Map<String, Object> safeContext = context == null ? Collections.emptyMap() : context;
        log.warn(
                "slow_operation event={} durationMs={} thresholdMs={} context={}",
                event,
                durationMs,
                thresholdMs,
                safeContext
        );
    }
}
