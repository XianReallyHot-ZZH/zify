package com.zify.common.web;

/**
 * Trace constants for HTTP requests and logs.
 */
public final class TraceConstants {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String TRACE_ID_MDC_KEY = "traceId";

    private TraceConstants() {
    }
}
