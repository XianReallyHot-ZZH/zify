package com.zify.tool.domain.mcp;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zify.common.persistence.id.IdGenerator;
import com.zify.tool.infrastructure.entity.McpServerEntity;
import com.zify.tool.infrastructure.entity.ToolEntity;
import com.zify.tool.infrastructure.mapper.ToolMapper;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * MCP 工具发现（glm-docs/13 §9.3 / P2 §八）。
 * <p>
 * 连接成功后 listTools → 写/更新 tool 表（source_type=MCP）：name 冲突加 mcpServerName__ 前缀；
 * refresh 增量同步（新增默认启用、已移除软删、已存在保留 enabled）。listTools（网络）在事务外，
 * DB 写各自短操作（不覆盖外部调用，13 §二）。
 */
@Component
public class McpDiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(McpDiscoveryService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final McpConnectionManager connectionManager;
    private final ToolMapper toolMapper;

    public McpDiscoveryService(McpConnectionManager connectionManager, ToolMapper toolMapper) {
        this.connectionManager = connectionManager;
        this.toolMapper = toolMapper;
    }

    /**
     * 发现并同步该 server 的工具（连接成功后调）。
     *
     * @return 同步到的工具数（-1 表示 server 未连接）
     */
    public int discoverAndSync(McpServerEntity server) {
        McpSyncClient client = connectionManager.getClient(server.getId());
        if (client == null) {
            return -1;
        }
        List<McpSchema.Tool> remoteTools;
        try {
            McpSchema.ListToolsResult result = client.listTools();
            remoteTools = result == null || result.tools() == null ? List.of() : result.tools();
        } catch (Exception e) {
            log.warn("MCP listTools failed for server {}: {}", server.getName(), e.getMessage());
            return -1;
        }
        applyDiscovered(server, remoteTools);
        return remoteTools.size();
    }

    private void applyDiscovered(McpServerEntity server, List<McpSchema.Tool> remoteTools) {
        String prefix = server.getName() + "__";

        // 本 server 既有工具（未删），按 remote base 名索引
        List<ToolEntity> mine = toolMapper.selectList(new LambdaQueryWrapper<ToolEntity>()
                .eq(ToolEntity::getMcpServerId, server.getId()));
        Map<String, ToolEntity> mineByBase = new HashMap<>();
        for (ToolEntity t : mine) {
            mineByBase.putIfAbsent(baseName(t.getName(), prefix), t);
        }

        // 全局未删工具名集合（用于新工具冲突检测；仅取 name 列）
        Set<String> allNames = toolMapper.selectList(new LambdaQueryWrapper<ToolEntity>()
                        .select(ToolEntity::getName)).stream()
                .map(ToolEntity::getName)
                .collect(Collectors.toSet());

        Set<String> seenBases = new HashSet<>();
        for (McpSchema.Tool rt : remoteTools) {
            String base = rt.name() == null ? "" : rt.name();
            if (base.isEmpty()) {
                continue;
            }
            seenBases.add(base);
            ToolEntity existing = mineByBase.get(base);
            String name;
            if (existing != null) {
                name = existing.getName(); // 保留（可能已加前缀）
            } else {
                name = allNames.contains(base) ? prefix + base : base;
                allNames.add(name);
            }
            upsert(server, rt, name, existing);
        }

        // server 端已移除的工具软删（保留 agent_tool 关联）
        for (ToolEntity t : mine) {
            if (!seenBases.contains(baseName(t.getName(), prefix))) {
                toolMapper.deleteById(t.getId());
            }
        }
        log.info("MCP discovery synced {} tool(s) for server {}", remoteTools.size(), server.getName());
    }

    private void upsert(McpServerEntity server, McpSchema.Tool rt, String name, ToolEntity existing) {
        String inputSchema = serialize(rt.inputSchema());
        String description = rt.description() == null ? "" : truncate(rt.description(), 512);
        if (existing != null) {
            existing.setName(name);
            existing.setDescription(description);
            existing.setInputSchema(inputSchema);
            toolMapper.updateById(existing);
        } else {
            ToolEntity entity = new ToolEntity();
            entity.setId(IdGenerator.uuid());
            entity.setName(name);
            entity.setDescription(description);
            entity.setSourceType("MCP");
            entity.setMcpServerId(server.getId());
            entity.setInputSchema(inputSchema);
            entity.setIdempotent(0); // MCP 默认非幂等
            entity.setEnabled(1);
            toolMapper.insert(entity);
        }
    }

    private String baseName(String toolName, String prefix) {
        return toolName != null && toolName.startsWith(prefix) ? toolName.substring(prefix.length()) : toolName;
    }

    private String serialize(Map<String, Object> schema) {
        if (schema == null) {
            return "{\"type\":\"object\"}";
        }
        try {
            return MAPPER.writeValueAsString(schema);
        } catch (Exception e) {
            return "{\"type\":\"object\"}";
        }
    }

    private static String truncate(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) : s;
    }
}
