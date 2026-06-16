package com.zify.tool.domain;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zify.common.exception.BusinessException;
import com.zify.common.exception.ErrorCode;
import com.zify.common.security.SecretEncryptor;
import com.zify.common.web.PageResult;
import com.zify.tool.api.dto.CreateMcpServerRequest;
import com.zify.tool.api.dto.McpDiscoveredToolResponse;
import com.zify.tool.api.dto.McpServerDetailResponse;
import com.zify.tool.api.dto.McpServerListQuery;
import com.zify.tool.api.dto.McpServerSummaryResponse;
import com.zify.tool.api.dto.McpServerTestResult;
import com.zify.tool.api.dto.McpToolPreview;
import com.zify.tool.api.dto.UpdateMcpServerRequest;
import com.zify.tool.domain.mcp.McpConnectionManager;
import com.zify.tool.domain.mcp.McpDiscoveryService;
import com.zify.tool.infrastructure.client.mcp.McpClientFactory;
import com.zify.tool.infrastructure.client.mcp.McpClientFactory.McpTestResult;
import com.zify.tool.infrastructure.client.ssrf.SsrfGuard;
import com.zify.tool.infrastructure.converter.McpServerConverter;
import com.zify.tool.infrastructure.entity.McpServerEntity;
import com.zify.tool.infrastructure.entity.ToolEntity;
import com.zify.tool.infrastructure.mapper.McpServerMapper;
import com.zify.tool.infrastructure.mapper.ToolMapper;
import com.zify.tool.infrastructure.security.AuthConfigs;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * MCP Server 管理（glm-docs/13 §9 / P2 §12.1）。
 * <p>
 * 所有方法不使用 @Transactional：connect/discover/disconnect/test 为网络调用，禁止入事务；
 * DB 写各自短操作自动提交（不覆盖外部调用）。
 */
@Service
public class McpServerService {

    private static final Logger log = LoggerFactory.getLogger(McpServerService.class);

    private final McpServerMapper mapper;
    private final ToolMapper toolMapper;
    private final McpConnectionManager connectionManager;
    private final McpDiscoveryService discoveryService;
    private final McpClientFactory clientFactory;
    private final SecretEncryptor secretEncryptor;
    private final SsrfGuard ssrfGuard;

    public McpServerService(McpServerMapper mapper, ToolMapper toolMapper, McpConnectionManager connectionManager,
                            McpDiscoveryService discoveryService, McpClientFactory clientFactory,
                            SecretEncryptor secretEncryptor, SsrfGuard ssrfGuard) {
        this.mapper = mapper;
        this.toolMapper = toolMapper;
        this.connectionManager = connectionManager;
        this.discoveryService = discoveryService;
        this.clientFactory = clientFactory;
        this.secretEncryptor = secretEncryptor;
        this.ssrfGuard = ssrfGuard;
    }

    public McpServerDetailResponse create(CreateMcpServerRequest request) {
        validateName(request.getName(), null);
        ssrfGuard.validateForSave(request.getBaseUrl(), request.getName());
        String plainAuth = AuthConfigs.forMcp(request.getAuthType(), request.getAuthHeaderName(), request.getCredential());
        String cipher = plainAuth != null ? secretEncryptor.encrypt(plainAuth) : null;

        McpServerEntity entity = McpServerConverter.toEntity(request, cipher);
        mapper.insert(entity);

        // 连接 + 发现（网络，在 DB 写之后、事务外）
        connectionManager.connect(entity);
        discoveryService.discoverAndSync(entity);
        return get(entity.getId());
    }

    public McpServerDetailResponse get(String id) {
        McpServerEntity entity = require(id);
        List<McpDiscoveredToolResponse> tools = discoveredTools(id);
        return McpServerConverter.toDetail(entity, tools);
    }

    public PageResult<McpServerSummaryResponse> list(McpServerListQuery query) {
        LambdaQueryWrapper<McpServerEntity> wrapper = new LambdaQueryWrapper<>();
        if (query.getEnabled() != null) {
            wrapper.eq(McpServerEntity::getEnabled, query.getEnabled());
        }
        if (query.getStatus() != null && !query.getStatus().isBlank()) {
            wrapper.eq(McpServerEntity::getStatus, query.getStatus());
        }
        wrapper.orderByDesc(McpServerEntity::getCreatedAt).orderByDesc(McpServerEntity::getId);

        Page<McpServerEntity> page = mapper.selectPage(new Page<>(query.getPage(), query.getPageSize()), wrapper);
        List<McpServerSummaryResponse> records = page.getRecords().stream()
                .map(e -> McpServerConverter.toSummary(e, toolsCount(e.getId())))
                .toList();
        return PageResult.of(records, page.getTotal(), query.getPage(), query.getPageSize());
    }

    public McpServerDetailResponse update(String id, UpdateMcpServerRequest request) {
        McpServerEntity entity = require(id);
        if (request.getName() != null) {
            validateName(request.getName(), id);
        }
        boolean reconnect = false;
        if (request.getBaseUrl() != null && !request.getBaseUrl().equals(entity.getBaseUrl())) {
            ssrfGuard.validateForSave(request.getBaseUrl(), entity.getName());
            reconnect = true;
        }
        String prevAuth = entity.getAuthConfig();
        String plainAuth = AuthConfigs.forMcp(request.getAuthType(), request.getAuthHeaderName(), request.getCredential());
        String cipher = plainAuth != null ? secretEncryptor.encrypt(plainAuth) : null;
        McpServerConverter.applyUpdate(entity, request, cipher);
        if (request.getCredential() != null && !request.getCredential().isBlank() && !authUnchanged(prevAuth, cipher)) {
            reconnect = true;
        }
        mapper.updateById(entity);

        Integer enabled = entity.getEnabled();
        if (enabled != null && enabled == 0) {
            connectionManager.disconnect(id);
        } else if (reconnect) {
            connectionManager.connect(entity);
            discoveryService.discoverAndSync(entity);
        }
        return get(id);
    }

    public void delete(String id) {
        McpServerEntity entity = require(id);
        connectionManager.disconnect(id);
        // 软删其下所有工具（保留 agent_tool 关联）
        toolMapper.delete(new LambdaQueryWrapper<ToolEntity>().eq(ToolEntity::getMcpServerId, id));
        mapper.deleteById(id);
        log.info("MCP server deleted: {}", entity.getName());
    }

    public McpServerDetailResponse setEnabled(String id, boolean enabled) {
        McpServerEntity entity = require(id);
        entity.setEnabled(enabled ? 1 : 0);
        mapper.updateById(entity);
        if (!enabled) {
            connectionManager.disconnect(id);
        } else {
            connectionManager.connect(entity);
            discoveryService.discoverAndSync(entity);
        }
        return get(id);
    }

    /** 测试已保存 server 的连接（POST /{id}/test）。 */
    public McpServerTestResult test(String id) {
        return doTest(require(id));
    }

    /** 测试未保存配置（POST /test）。 */
    public McpServerTestResult testConfig(CreateMcpServerRequest request) {
        try {
            ssrfGuard.validate(request.getBaseUrl(), null, request.getName(), "mcp_test");
        } catch (com.zify.tool.infrastructure.exception.ToolNonRetryableException e) {
            McpServerTestResult blocked = new McpServerTestResult();
            blocked.setSuccess(false);
            blocked.setMessage("URL 命中 SSRF 黑名单");
            blocked.setLatencyMs(0);
            blocked.setDiscoveredTools(List.of());
            return blocked;
        }
        String plainAuth = AuthConfigs.forMcp(request.getAuthType(), request.getAuthHeaderName(), request.getCredential());
        String cipher = plainAuth != null ? secretEncryptor.encrypt(plainAuth) : null;
        McpServerEntity temp = new McpServerEntity();
        temp.setBaseUrl(request.getBaseUrl());
        temp.setTransportType(request.getTransportType());
        temp.setAuthType(request.getAuthType());
        temp.setAuthConfig(cipher);
        return doTest(temp);
    }

    /** 刷新发现（POST /{id}/refresh）。 */
    public McpServerDetailResponse refresh(String id) {
        McpServerEntity entity = require(id);
        if (!connectionManager.isOnline(id)) {
            connectionManager.connect(entity);
        }
        discoveryService.discoverAndSync(entity);
        return get(id);
    }

    // ── 内部 ───────────────────────────────────────────────

    private McpServerTestResult doTest(McpServerEntity server) {
        try {
            ssrfGuard.validate(server.getBaseUrl(), server.getId(), server.getName(), "mcp_test");
        } catch (com.zify.tool.infrastructure.exception.ToolNonRetryableException e) {
            McpServerTestResult blocked = new McpServerTestResult();
            blocked.setSuccess(false);
            blocked.setMessage("URL 命中 SSRF 黑名单");
            blocked.setLatencyMs(0);
            blocked.setDiscoveredTools(List.of());
            return blocked;
        }
        long start = System.currentTimeMillis();
        McpTestResult res = clientFactory.test(server);
        long latency = System.currentTimeMillis() - start;

        McpServerTestResult result = new McpServerTestResult();
        result.setSuccess(res.isSuccess());
        result.setMessage(res.getMessage());
        result.setLatencyMs(latency);
        List<McpToolPreview> previews = res.getTools().stream()
                .map(this::toPreview)
                .toList();
        result.setDiscoveredTools(previews);
        return result;
    }

    private McpToolPreview toPreview(McpSchema.Tool tool) {
        String schema = tool.inputSchema() == null ? null : tool.inputSchema().toString();
        return new McpToolPreview(tool.name(), tool.description(), schema);
    }

    private List<McpDiscoveredToolResponse> discoveredTools(String serverId) {
        return toolMapper.selectList(new LambdaQueryWrapper<ToolEntity>()
                        .eq(ToolEntity::getMcpServerId, serverId)
                        .orderByDesc(ToolEntity::getCreatedAt)).stream()
                .map(t -> {
                    McpDiscoveredToolResponse d = new McpDiscoveredToolResponse();
                    d.setId(t.getId());
                    d.setName(t.getName());
                    d.setDescription(t.getDescription());
                    d.setSourceType(t.getSourceType());
                    d.setEnabled(t.getEnabled());
                    return d;
                })
                .toList();
    }

    private long toolsCount(String serverId) {
        return toolMapper.selectCount(new LambdaQueryWrapper<ToolEntity>()
                .eq(ToolEntity::getMcpServerId, serverId));
    }

    private void validateName(String name, String excludeId) {
        if (name == null || name.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "name is required");
        }
        LambdaQueryWrapper<McpServerEntity> wrapper = new LambdaQueryWrapper<McpServerEntity>()
                .eq(McpServerEntity::getName, name);
        if (excludeId != null) {
            wrapper.ne(McpServerEntity::getId, excludeId);
        }
        if (mapper.selectCount(wrapper) > 0) {
            throw new BusinessException(ErrorCode.MCP_SERVER_NAME_DUPLICATE);
        }
    }

    private boolean authUnchanged(String prevCipher, String newCipher) {
        return (prevCipher == null && newCipher == null)
                || (prevCipher != null && prevCipher.equals(newCipher));
    }

    private McpServerEntity require(String id) {
        McpServerEntity entity = mapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.MCP_SERVER_NOT_FOUND);
        }
        return entity;
    }
}
