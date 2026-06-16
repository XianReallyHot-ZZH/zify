package com.zify.tool.infrastructure.facade;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zify.tool.api.ToolFacade;
import com.zify.tool.api.dto.ToolExecutionCommand;
import com.zify.tool.api.dto.ToolExecutionResultDTO;
import com.zify.tool.api.dto.ToolExecContext;
import com.zify.tool.api.dto.ToolViewDTO;
import com.zify.tool.domain.Tool;
import com.zify.tool.domain.ToolExecutionResult;
import com.zify.tool.domain.http.HttpTool;
import com.zify.tool.domain.mcp.McpConnectionManager;
import com.zify.tool.domain.mcp.McpTool;
import com.zify.tool.infrastructure.client.ToolExecSupport;
import com.zify.tool.infrastructure.client.http.HttpClientFactory;
import com.zify.tool.infrastructure.entity.ToolEntity;
import com.zify.tool.infrastructure.exception.ToolCancelledException;
import com.zify.tool.infrastructure.mapper.ToolMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * 工具模块 Facade 实现（glm-docs/13 §二 / P2 §9）。
 * <p>
 * listAvailableTools 过滤可用（enabled+未删；MCP 工具需其 server 在线）；
 * executeTool 按 source_type 选 HttpTool/McpTool，全局 Semaphore 限并发，
 * 失败返回 status=ERROR（不抛），仅 {@link ToolCancelledException} 致命上抛。
 */
@Service
public class ToolFacadeImpl implements ToolFacade {

    private static final Logger log = LoggerFactory.getLogger(ToolFacadeImpl.class);

    private final ToolMapper toolMapper;
    private final ToolExecSupport support;
    private final HttpClientFactory httpClientFactory;
    private final McpConnectionManager connectionManager;
    private final Semaphore toolSemaphore;

    public ToolFacadeImpl(ToolMapper toolMapper, ToolExecSupport support, HttpClientFactory httpClientFactory,
                          McpConnectionManager connectionManager,
                          @Qualifier("toolSemaphore") Semaphore toolSemaphore) {
        this.toolMapper = toolMapper;
        this.support = support;
        this.httpClientFactory = httpClientFactory;
        this.connectionManager = connectionManager;
        this.toolSemaphore = toolSemaphore;
    }

    @Override
    public List<ToolViewDTO> listAvailableTools(Collection<String> toolIds) {
        if (toolIds == null || toolIds.isEmpty()) {
            return List.of();
        }
        // 显式列字段，禁止 SELECT *（input_schema 运行时透传 LLM 必需）
        List<ToolEntity> tools = toolMapper.selectList(new LambdaQueryWrapper<ToolEntity>()
                .in(ToolEntity::getId, toolIds)
                .eq(ToolEntity::getEnabled, 1)
                .select(ToolEntity::getId, ToolEntity::getName, ToolEntity::getDescription,
                        ToolEntity::getInputSchema, ToolEntity::getSourceType, ToolEntity::getMcpServerId));

        List<ToolViewDTO> available = new ArrayList<>();
        for (ToolEntity tool : tools) {
            if (!isAvailable(tool)) {
                continue;
            }
            available.add(new ToolViewDTO(tool.getId(), tool.getName(), tool.getDescription(),
                    tool.getInputSchema(), tool.getSourceType()));
        }
        return available;
    }

    @Override
    public ToolExecutionResultDTO executeTool(ToolExecutionCommand command) {
        String toolId = command.getToolId();
        ToolEntity tool = toolMapper.selectById(toolId);
        if (tool == null) {
            log.warn("event=tool_call toolId={} status=rejected reason=not_found", toolId);
            return new ToolExecutionResultDTO("ERROR", "工具不存在", 0, null, "tool not found");
        }
        String toolName = tool.getName();
        if (tool.getEnabled() == null || tool.getEnabled() != 1) {
            log.warn("event=tool_call tool={} status=rejected reason=disabled", toolName);
            return new ToolExecutionResultDTO("ERROR", "工具 " + toolName + " 已禁用", 0, null, "tool disabled");
        }
        if ("WORKFLOW".equals(tool.getSourceType())) {
            return new ToolExecutionResultDTO("ERROR", "工作流工具暂不支持", 0, null, "workflow-as-tool not supported");
        }

        Tool toolImpl = buildTool(tool);
        ToolExecContext ctx = command.getContext();

        boolean acquired;
        try {
            acquired = toolSemaphore.tryAcquire(
                    support.getProperties().getExecutor().getAcquireTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ToolCancelledException("interrupted while waiting for tool permit",
                    toolId, toolName, "execute", e);
        }
        if (!acquired) {
            log.warn("event=tool_call tool={} status=rejected reason=busy", toolName);
            return new ToolExecutionResultDTO("ERROR", "工具 " + toolName + " 当前繁忙，请稍后重试",
                    0, null, "global concurrency limit reached");
        }
        try {
            ToolExecutionResult result = toolImpl.execute(command.getArgs(), ctx);
            return new ToolExecutionResultDTO(result.getStatus(), result.getOutput(), result.getDurationMs(),
                    result.getToolCallLogId(), result.getError());
        } catch (ToolCancelledException e) {
            // 致命（取消）：上抛 engine 中断循环
            throw e;
        } finally {
            toolSemaphore.release();
        }
    }

    private boolean isAvailable(ToolEntity tool) {
        if (!"MCP".equals(tool.getSourceType())) {
            return true; // HTTP（WORKFLOW 在 P2 不产生）
        }
        return tool.getMcpServerId() != null && connectionManager.isOnline(tool.getMcpServerId());
    }

    private Tool buildTool(ToolEntity tool) {
        if ("MCP".equals(tool.getSourceType())) {
            return new McpTool(tool, support, connectionManager);
        }
        return new HttpTool(tool, support, httpClientFactory);
    }
}
