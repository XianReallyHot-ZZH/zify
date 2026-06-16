package com.zify.tool.domain.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zify.common.persistence.id.IdGenerator;
import com.zify.tool.api.dto.ToolExecContext;
import com.zify.tool.domain.Tool;
import com.zify.tool.domain.ToolExecutionResult;
import com.zify.tool.domain.ToolView;
import com.zify.tool.infrastructure.client.ToolExecSupport;
import com.zify.tool.infrastructure.entity.ToolCallLogEntity;
import com.zify.tool.infrastructure.entity.ToolEntity;
import com.zify.tool.infrastructure.exception.ToolCircuitOpenException;
import com.zify.tool.infrastructure.exception.ToolCancelledException;
import com.zify.tool.infrastructure.exception.ToolException;
import com.zify.tool.infrastructure.exception.ToolNonRetryableException;
import com.zify.tool.infrastructure.exception.ToolRetryableException;
import com.zify.tool.infrastructure.exception.ToolTimeoutException;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MCP 工具实现（glm-docs/13 §9.2 / P2 §八）。
 * <p>
 * 通过 {@link McpConnectionManager} 取常驻 client；未连接降级返回 ERROR（不抛）。
 * 执行经熔断 + 重试（MCP 默认非幂等），结果截断，写 tool_call_log（source_type=MCP）。
 */
public class McpTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(McpTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String SCENARIO = "mcp_call";

    private final ToolEntity snapshot;
    private final ToolExecSupport support;
    private final McpConnectionManager connectionManager;

    public McpTool(ToolEntity snapshot, ToolExecSupport support, McpConnectionManager connectionManager) {
        this.snapshot = snapshot;
        this.support = support;
        this.connectionManager = connectionManager;
    }

    @Override
    public ToolView toView() {
        return new ToolView(snapshot.getId(), snapshot.getName(), snapshot.getDescription(),
                snapshot.getInputSchema(), snapshot.getSourceType());
    }

    @Override
    public ToolExecutionResult execute(Map<String, Object> args, ToolExecContext ctx) {
        Map<String, Object> safeArgs = args == null ? Collections.emptyMap() : args;
        long start = System.nanoTime();
        String toolId = snapshot.getId();
        String toolName = snapshot.getName();
        boolean idempotent = snapshot.getIdempotent() != null && snapshot.getIdempotent() == 1;

        McpSyncClient client = connectionManager.getClient(snapshot.getMcpServerId());
        if (client == null) {
            // 未连接：降级，不抛（13 §6.2 熔断/不可用回灌）
            long durationMs = durationMs(start);
            String logId = writeLog(ctx, safeArgs, null, "ERROR", durationMs, "mcp server offline");
            log.warn("event=tool_call scenario={} source=MCP tool={} status=unavailable", SCENARIO, toolName);
            return new ToolExecutionResult("ERROR", "工具 " + toolName + " 当前不可用", durationMs, logId,
                    "mcp server offline");
        }

        Duration budget = requestBudget();
        Instant deadline = Instant.now().plus(budget);
        try {
            McpSchema.CallToolResult result = support.getCircuitBreaker().execute(toolId, toolName, SCENARIO,
                    () -> support.getRetryWrapper().withRetry(idempotent, deadline,
                            () -> callTool(client, toolName, safeArgs), toolId, toolName, SCENARIO));

            long durationMs = durationMs(start);
            String rawOutput = extractOutput(result);
            boolean isError = result != null && Boolean.TRUE.equals(result.isError());
            String output = support.getResponseSizer().truncate(rawOutput);
            if (isError) {
                String err = friendly("MCP tool reported error: " + output);
                String logId = writeLog(ctx, safeArgs, output, "ERROR", durationMs, err);
                log.warn("event=tool_call scenario={} source=MCP tool={} status=error(tool) durationMs={}",
                        SCENARIO, toolName, durationMs);
                return new ToolExecutionResult("ERROR", "工具 " + toolName + " 执行失败：" + friendly(output),
                        durationMs, logId, err);
            }
            String logId = writeLog(ctx, safeArgs, output, "SUCCESS", durationMs, null);
            log.info("event=tool_call scenario={} source=MCP tool={} status=success durationMs={}",
                    SCENARIO, toolName, durationMs);
            return new ToolExecutionResult("SUCCESS", output, durationMs, logId, null);
        } catch (ToolCancelledException e) {
            log.info("event=tool_call scenario={} source=MCP tool={} status=cancelled", SCENARIO, toolName);
            throw e;
        } catch (ToolException e) {
            long durationMs = durationMs(start);
            String status = mapStatus(e);
            String err = friendlyError(e);
            String logId = writeLog(ctx, safeArgs, null, status, durationMs, err);
            String fallback = fallbackText(toolName, e);
            log.warn("event=tool_call scenario={} source=MCP tool={} status={} durationMs={} error={}",
                    SCENARIO, toolName, status, durationMs, err);
            return new ToolExecutionResult("ERROR", fallback, durationMs, logId, err);
        }
    }

    private McpSchema.CallToolResult callTool(McpSyncClient client, String toolName, Map<String, Object> args) {
        try {
            return client.callTool(new McpSchema.CallToolRequest(toolName, args));
        } catch (Exception e) {
            throw classify(e, toolName);
        }
    }

    private ToolException classify(Throwable t, String toolName) {
        if (t instanceof ToolException te) {
            return te;
        }
        String msg = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
        String cn = t.getClass().getName();
        if (cn.contains("Timeout") || cn.contains("IOException") || cn.contains("ConnectException")) {
            return new ToolRetryableException("mcp call io error: " + brief(msg),
                    snapshot.getId(), toolName, SCENARIO, false);
        }
        return new ToolNonRetryableException("mcp call failed: " + brief(msg),
                snapshot.getId(), toolName, SCENARIO, t);
    }

    private String extractOutput(McpSchema.CallToolResult result) {
        if (result == null || result.content() == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (McpSchema.Content c : result.content()) {
            if (c instanceof McpSchema.TextContent tc) {
                sb.append(tc.text());
            } else {
                sb.append(c.toString());
            }
        }
        return sb.toString();
    }

    // ── 日志（与 HttpTool 同结构：执行点即记录点） ─────────

    private String writeLog(ToolExecContext ctx, Map<String, Object> args, String output,
                            String status, long durationMs, String error) {
        ToolCallLogEntity entity = new ToolCallLogEntity();
        entity.setId(IdGenerator.uuid());
        entity.setToolId(snapshot.getId());
        entity.setToolName(snapshot.getName());
        entity.setSourceType(snapshot.getSourceType());
        entity.setMcpServerId(snapshot.getMcpServerId());
        if (ctx != null) {
            entity.setAgentId(ctx.getAgentId());
            entity.setConversationId(ctx.getConversationId());
            entity.setTurn(ctx.getTurn());
            entity.setToolCallId(ctx.getToolCallId());
        }
        entity.setInput(truncateInput(args));
        entity.setOutput(output);
        entity.setStatus(status);
        entity.setDurationMs((int) Math.min(durationMs, Integer.MAX_VALUE));
        entity.setError(error);
        support.getToolCallLogMapper().insert(entity);
        return entity.getId();
    }

    private Map<String, Object> truncateInput(Map<String, Object> args) {
        if (args == null || args.isEmpty()) {
            return null;
        }
        try {
            String json = MAPPER.writeValueAsString(args);
            int cap = support.getProperties().getSecurity().getResponseMaxBytes();
            if (json.getBytes(StandardCharsets.UTF_8).length <= cap) {
                return args;
            }
            String truncated = support.getResponseSizer().truncate(json, cap);
            try {
                return MAPPER.readValue(truncated, Map.class);
            } catch (Exception invalid) {
                Map<String, Object> wrap = new LinkedHashMap<>();
                wrap.put("_input_truncated", truncated);
                return wrap;
            }
        } catch (Exception e) {
            return null;
        }
    }

    private String fallbackText(String toolName, ToolException e) {
        if (e instanceof ToolCircuitOpenException) {
            return "工具 " + toolName + " 当前不可用";
        }
        if (e instanceof ToolTimeoutException || e instanceof ToolRetryableException) {
            return "工具 " + toolName + " 暂时不可用，请稍后重试或换一种方式";
        }
        return "工具 " + toolName + " 执行失败：" + friendlyError(e);
    }

    private String mapStatus(ToolException e) {
        if (e instanceof ToolTimeoutException) {
            return "TIMEOUT";
        }
        if (e instanceof ToolCircuitOpenException) {
            return "CIRCUIT_OPEN";
        }
        return "ERROR";
    }

    private String friendlyError(ToolException e) {
        return friendly(e.getMessage());
    }

    private String friendly(String msg) {
        if (msg == null || msg.isBlank()) {
            return "unknown error";
        }
        return msg.length() > 200 ? msg.substring(0, 200) : msg;
    }

    private static String brief(String msg) {
        return msg.length() > 120 ? msg.substring(0, 120) : msg;
    }

    private Duration requestBudget() {
        Duration requestTimeout = snapshot.getTimeoutSeconds() != null
                ? Duration.ofSeconds(snapshot.getTimeoutSeconds())
                : support.getProperties().getTimeout().getRequestDefault();
        Duration totalCap = support.getProperties().getTimeout().getTotalCap();
        return requestTimeout.compareTo(totalCap) <= 0 ? requestTimeout : totalCap;
    }

    private long durationMs(long startNanos) {
        return Math.max(0, (System.nanoTime() - startNanos) / 1_000_000L);
    }
}
