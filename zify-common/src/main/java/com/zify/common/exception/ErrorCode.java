package com.zify.common.exception;

/**
 * 业务错误码枚举
 */
public enum ErrorCode {

    // ── 成功 ─────────────────────────────────────────────────
    SUCCESS(200, "success"),

    // ── 通用 4xx ──────────────────────────────────────────
    PARAM_ERROR(400, "请求参数错误"),
    UNAUTHORIZED(401, "未认证"),
    FORBIDDEN(403, "无权限"),
    NOT_FOUND(404, "资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方法不允许"),
    CONFLICT(409, "资源冲突"),
    JSON_PARSE_ERROR(4001, "JSON 解析失败"),

    // ── 业务 ──────────────────────────────────────────────
    AGENT_NOT_FOUND(1001, "Agent 不存在"),
    MODEL_PROVIDER_ERROR(1101, "模型服务调用失败"),
    KNOWLEDGE_NOT_FOUND(1201, "知识库不存在"),
    WORKFLOW_NOT_FOUND(1301, "工作流不存在"),
    TOOL_NOT_FOUND(1401, "工具不存在"),

    // ── 通用 5xx ──────────────────────────────────────────
    INTERNAL_ERROR(500, "服务器内部错误"),
    EXTERNAL_CALL_FAILED(5001, "外部调用失败");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
