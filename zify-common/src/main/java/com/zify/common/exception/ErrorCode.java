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
    AGENT_NAME_DUPLICATE(1002, "Agent 名称已存在"),
    AGENT_TYPE_INVALID(1003, "Agent 类型非法"),
    AGENT_TYPE_IMMUTABLE(1004, "Agent 类型不可修改"),
    AGENT_INACTIVE(1005, "Agent 已禁用"),
    MODEL_PROVIDER_ERROR(1101, "模型服务调用失败"),
    PROVIDER_NAME_DUPLICATE(1102, "供应商名称已存在"),
    PROVIDER_NOT_FOUND(1103, "供应商不存在"),
    PROVIDER_TYPE_IMMUTABLE(1104, "供应商类型不可修改"),
    MODEL_NAME_DUPLICATE(1105, "同一供应商下模型标识已存在"),
    MODEL_NOT_FOUND(1106, "模型不存在"),
    MODEL_NAME_IMMUTABLE(1107, "模型标识不可修改"),
    MODEL_PROVIDER_IMMUTABLE(1108, "模型所属供应商不可修改"),
    MODEL_UNAVAILABLE(1109, "模型不可用"),
    PROVIDER_TEST_ERROR(1110, "供应商连接测试失败"),
    MODEL_TEST_ERROR(1111, "模型可用性测试失败"),
    KNOWLEDGE_NOT_FOUND(1201, "知识库不存在"),
    WORKFLOW_NOT_FOUND(1301, "工作流不存在"),
    TOOL_NOT_FOUND(1401, "工具不存在"),
    MCP_SERVER_NOT_FOUND(1402, "MCP Server 不存在"),
    TOOL_NAME_DUPLICATE(1403, "工具名称已存在"),
    MCP_SERVER_NAME_DUPLICATE(1404, "MCP Server 名称已存在"),
    OPENAPI_PARSE_FAILED(1405, "OpenAPI 解析失败"),
    TOOL_SSRF_BLOCKED(1406, "URL 命中 SSRF 黑名单"),
    TOOL_CONFIG_INVALID(1407, "工具配置非法"),
    TOOL_NOT_AVAILABLE(1408, "工具不可用"),
    CONVERSATION_NOT_FOUND(1501, "会话不存在"),
    CONVERSATION_NOT_ACTIVE(1502, "会话非活跃状态"),
    MESSAGE_CONTENT_EMPTY(1503, "消息内容为空"),
    MESSAGE_TOO_LONG(1504, "消息内容过长"),
    CHAT_TURN_FAILED(1505, "对话生成失败"),

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
