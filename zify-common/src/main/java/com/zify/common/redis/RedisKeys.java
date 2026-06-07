package com.zify.common.redis;

/**
 * Redis key naming utilities.
 * <p>
 * This class only generates keys and does not access Redis.
 */
public final class RedisKeys {

    private RedisKeys() {
    }

    public static String chatContext(String conversationId) {
        return "chat:ctx:" + conversationId;
    }

    public static String documentParseProgress(String documentId) {
        return "doc:parse:" + documentId;
    }

    public static String workflowRunState(String runId) {
        return "workflow:run:" + runId;
    }

    public static String sseState(String runId) {
        return "sse:" + runId;
    }

    public static String rateLimit(String userId, String api) {
        return "rate:" + userId + ":" + api;
    }
}
