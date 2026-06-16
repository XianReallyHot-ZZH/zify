package com.zify.tool.domain;

import com.zify.tool.api.dto.ToolExecContext;

import java.util.Map;

/**
 * tool 模块内部统一工具接口（不跨 Facade，glm-docs/13 §二 / P2 §3.3）。
 * <p>
 * {@link com.zify.tool.domain.http.HttpTool} / {@link com.zify.tool.domain.mcp.McpTool} 各自实现；
 * {@code ToolFacadeImpl} 按 toolId 选实现执行。对外只暴露中立 DTO。
 */
public interface Tool {

    /** 工具视图（喂给 LLM 的 name/description/inputSchema）。 */
    ToolView toView();

    /**
     * 执行工具。失败返回 {@code status=ERROR}（不抛，由 Facade 转 DTO 回灌模型）；
     * 仅致命错误（取消等）抛 {@link com.zify.tool.infrastructure.exception.ToolException}。
     */
    ToolExecutionResult execute(Map<String, Object> args, ToolExecContext ctx);
}
