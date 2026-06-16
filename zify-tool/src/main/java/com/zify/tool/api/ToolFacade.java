package com.zify.tool.api;

import com.zify.tool.api.dto.ToolExecutionCommand;
import com.zify.tool.api.dto.ToolExecutionResultDTO;
import com.zify.tool.api.dto.ToolViewDTO;

import java.util.Collection;
import java.util.List;

/**
 * 工具模块 Facade（中立 DTO，供 engine/agent 跨模块调用），glm-docs/13 §二 / P2 §3.1。
 * <p>
 * 边界修正：不提供 listBoundTools(agentId)——agent_tool 归 agent 模块，tool 不能跨模块读。
 * engine 先 AgentFacade.getBoundToolIds(agentId) 再 ToolFacade.listAvailableTools(ids)。
 */
public interface ToolFacade {

    /**
     * 返回这些 ID 中可用的工具视图（过滤 enabled=1 / 未删 / 来源可用：
     * source_type=HTTP 或 source_type=MCP 且对应 mcp_server.status=ONLINE）。
     */
    List<ToolViewDTO> listAvailableTools(Collection<String> toolIds);

    /**
     * 执行工具：内部完成 SSRF 运行时校验、超时/重试/熔断、截断、写 tool_call_log，
     * 返回中立 DTO。失败返回 status=ERROR（不抛），仅致命错误（取消等）由调用方处理。
     */
    ToolExecutionResultDTO executeTool(ToolExecutionCommand command);
}
