package com.zify.tool.domain;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zify.common.exception.BusinessException;
import com.zify.common.exception.ErrorCode;
import com.zify.common.security.SecretEncryptor;
import com.zify.common.web.PageResult;
import com.zify.tool.api.ToolFacade;
import com.zify.tool.api.dto.CreateToolRequest;
import com.zify.tool.api.dto.ImportOpenApiRequest;
import com.zify.tool.api.dto.OpenApiOperationPreviewResponse;
import com.zify.tool.api.dto.OpenApiParseResponse;
import com.zify.tool.api.dto.ToolDetailResponse;
import com.zify.tool.api.dto.ToolExecutionCommand;
import com.zify.tool.api.dto.ToolExecutionResultDTO;
import com.zify.tool.api.dto.ToolImportResult;
import com.zify.tool.api.dto.ToolListQuery;
import com.zify.tool.api.dto.ToolSummaryResponse;
import com.zify.tool.api.dto.ToolTestResult;
import com.zify.tool.api.dto.UpdateToolRequest;
import com.zify.tool.domain.mcp.McpConnectionManager;
import com.zify.tool.domain.openapi.AuthSpec;
import com.zify.tool.domain.openapi.OpenApiParseResult;
import com.zify.tool.domain.openapi.OperationPreview;
import com.zify.tool.domain.openapi.ToolBuildPlan;
import com.zify.tool.infrastructure.client.ssrf.SsrfGuard;
import com.zify.tool.infrastructure.converter.ToolConverter;
import com.zify.tool.infrastructure.entity.McpServerEntity;
import com.zify.tool.infrastructure.entity.ToolEntity;
import com.zify.tool.infrastructure.mapper.McpServerMapper;
import com.zify.tool.infrastructure.mapper.ToolMapper;
import com.zify.tool.infrastructure.security.AuthConfigs;
import com.zify.tool.domain.openapi.OpenApiParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 工具管理（HTTP CRUD + OpenAPI 导入 + 测试，glm-docs/13 §十 / P2 §12.2）。
 */
@Service
public class ToolService {

    private static final Logger log = LoggerFactory.getLogger(ToolService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ToolMapper toolMapper;
    private final McpServerMapper mcpServerMapper;
    private final ToolFacade toolFacade;
    private final SecretEncryptor secretEncryptor;
    private final SsrfGuard ssrfGuard;
    private final OpenApiParser openApiParser;
    private final McpConnectionManager connectionManager;

    public ToolService(ToolMapper toolMapper, McpServerMapper mcpServerMapper, ToolFacade toolFacade,
                       SecretEncryptor secretEncryptor, SsrfGuard ssrfGuard, OpenApiParser openApiParser,
                       McpConnectionManager connectionManager) {
        this.toolMapper = toolMapper;
        this.mcpServerMapper = mcpServerMapper;
        this.toolFacade = toolFacade;
        this.secretEncryptor = secretEncryptor;
        this.ssrfGuard = ssrfGuard;
        this.openApiParser = openApiParser;
        this.connectionManager = connectionManager;
    }

    public ToolDetailResponse create(CreateToolRequest request) {
        validateName(request.getName(), null);
        requireHttpConfig(request.getMethod(), request.getEndpoint());
        ssrfGuard.validateForSave(request.getEndpoint(), request.getName());
        int idempotent = request.getIdempotent() != null ? request.getIdempotent() : inferIdempotent(request.getMethod());
        String cipher = encryptAuth(request.getAuthType(), request.getAuthHeaderName(), request.getCredential());

        ToolEntity entity = ToolConverter.toEntity(request, cipher, idempotent);
        toolMapper.insert(entity);
        return get(entity.getId());
    }

    public OpenApiParseResponse parseOpenApi(String specContent) {
        OpenApiParseResult result = openApiParser.parse(specContent);
        OpenApiParseResponse response = new OpenApiParseResponse();
        response.setBaseUrl(result.getBaseUrl());
        List<OpenApiOperationPreviewResponse> ops = new ArrayList<>();
        for (OperationPreview op : result.getOperations()) {
            OpenApiOperationPreviewResponse o = new OpenApiOperationPreviewResponse();
            o.setOperationId(op.getOperationId());
            o.setMethod(op.getMethod());
            o.setPath(op.getPath());
            o.setSummary(op.getSummary());
            o.setSuggestedName(op.getSuggestedName());
            o.setHasRequestBody(op.isHasRequestBody());
            ops.add(o);
        }
        response.setOperations(ops);
        return response;
    }

    public ToolImportResult importOpenApi(ImportOpenApiRequest request) {
        AuthSpec authSpec = new AuthSpec();
        authSpec.setType(request.getAuthType() == null ? "NONE" : request.getAuthType());
        authSpec.setHeaderName(request.getAuthHeaderName());
        String cipher = encryptAuth(request.getAuthType(), request.getAuthHeaderName(), request.getCredential());

        List<ToolBuildPlan> plans = openApiParser.buildPlans(request.getSpec(), request.getBaseUrl(),
                request.getOperations(), authSpec);

        List<ToolDetailResponse> created = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        Set<String> usedInBatch = new HashSet<>();
        for (ToolBuildPlan plan : plans) {
            String uniqueName = ensureUniqueName(plan.getName(), usedInBatch);
            plan.setName(uniqueName);
            usedInBatch.add(uniqueName);
            ToolEntity entity = ToolConverter.fromBuildPlan(plan, cipher);
            try {
                toolMapper.insert(entity);
                created.add(get(entity.getId()));
            } catch (Exception e) {
                log.warn("OpenAPI import skipped {}: {}", uniqueName, e.getMessage());
                skipped.add(uniqueName);
            }
        }
        ToolImportResult result = new ToolImportResult();
        result.setCreated(created);
        result.setSkipped(skipped);
        return result;
    }

    public PageResult<ToolSummaryResponse> list(ToolListQuery query) {
        LambdaQueryWrapper<ToolEntity> wrapper = new LambdaQueryWrapper<>();
        if (query.getSourceType() != null && !query.getSourceType().isBlank()) {
            wrapper.eq(ToolEntity::getSourceType, query.getSourceType());
        }
        if (query.getMcpServerId() != null && !query.getMcpServerId().isBlank()) {
            wrapper.eq(ToolEntity::getMcpServerId, query.getMcpServerId());
        }
        if (query.getEnabled() != null) {
            wrapper.eq(ToolEntity::getEnabled, query.getEnabled());
        }
        wrapper.orderByDesc(ToolEntity::getCreatedAt).orderByDesc(ToolEntity::getId);

        Page<ToolEntity> page = toolMapper.selectPage(new Page<>(query.getPage(), query.getPageSize()), wrapper);
        List<ToolSummaryResponse> records = page.getRecords().stream()
                .map(e -> ToolConverter.toSummary(e, mcpServerName(e.getMcpServerId()), availability(e)))
                .toList();
        return PageResult.of(records, page.getTotal(), query.getPage(), query.getPageSize());
    }

    public ToolDetailResponse get(String id) {
        ToolEntity entity = require(id);
        return ToolConverter.toDetail(entity, mcpServerName(entity.getMcpServerId()), availability(entity),
                resolveAuthType(entity));
    }

    public ToolDetailResponse update(String id, UpdateToolRequest request) {
        ToolEntity entity = require(id);
        if (!"HTTP".equals(entity.getSourceType())) {
            throw new BusinessException(ErrorCode.TOOL_CONFIG_INVALID, "MCP 工具不可编辑配置");
        }
        if (request.getName() != null) {
            validateName(request.getName(), id);
        }
        if (request.getEndpoint() != null) {
            requireHttpConfig(request.getMethod() != null ? request.getMethod() : entity.getMethod(), request.getEndpoint());
            ssrfGuard.validateForSave(request.getEndpoint(), entity.getName());
        }
        Integer idempotent = request.getIdempotent();
        String cipher = encryptAuth(request.getAuthType(), request.getAuthHeaderName(), request.getCredential());
        ToolConverter.applyUpdate(entity, request, cipher, idempotent);
        toolMapper.updateById(entity);
        return get(id);
    }

    public void delete(String id) {
        ToolEntity entity = require(id);
        toolMapper.deleteById(id);
        log.info("Tool deleted: {} ({})", entity.getName(), entity.getSourceType());
    }

    public ToolDetailResponse setEnabled(String id, boolean enabled) {
        ToolEntity entity = require(id);
        entity.setEnabled(enabled ? 1 : 0);
        toolMapper.updateById(entity);
        return get(id);
    }

    public ToolTestResult test(String id, Map<String, Object> args) {
        require(id);
        ToolExecutionCommand command = new ToolExecutionCommand(id, args, null);
        ToolExecutionResultDTO result = toolFacade.executeTool(command);
        ToolTestResult test = new ToolTestResult();
        test.setSuccess("SUCCESS".equals(result.getStatus()));
        test.setStatus(result.getStatus());
        test.setOutput(result.getOutput());
        test.setDurationMs(result.getDurationMs());
        test.setError(result.getError());
        test.setToolCallLogId(result.getToolCallLogId());
        return test;
    }

    // ── 内部 ───────────────────────────────────────────────

    private int inferIdempotent(String method) {
        if (method == null) {
            return 0;
        }
        String m = method.toUpperCase();
        return ("GET".equals(m) || "HEAD".equals(m)) ? 1 : 0;
    }

    private String encryptAuth(String authType, String headerName, String credential) {
        String plain = AuthConfigs.forTool(authType, headerName, credential);
        return plain != null ? secretEncryptor.encrypt(plain) : null;
    }

    @SuppressWarnings("unchecked")
    private String resolveAuthType(ToolEntity entity) {
        if (!ToolConverter.hasAuth(entity)) {
            return "NONE";
        }
        try {
            String plain = secretEncryptor.decrypt(entity.getAuthConfig());
            if (plain == null) {
                return "NONE";
            }
            Object type = MAPPER.readValue(plain, Map.class).get("type");
            return type == null ? "NONE" : String.valueOf(type);
        } catch (Exception e) {
            return "NONE";
        }
    }

    private String ensureUniqueName(String candidate, Set<String> usedInBatch) {
        String name = candidate == null || candidate.isBlank() ? "tool" : candidate;
        String base = name;
        int i = 2;
        while (usedInBatch.contains(name) || nameExists(name)) {
            name = base + "_" + i;
            i++;
        }
        return name;
    }

    private boolean nameExists(String name) {
        return toolMapper.selectCount(new LambdaQueryWrapper<ToolEntity>().eq(ToolEntity::getName, name)) > 0;
    }

    private String mcpServerName(String mcpServerId) {
        if (mcpServerId == null) {
            return null;
        }
        McpServerEntity server = mcpServerMapper.selectById(mcpServerId);
        return server == null ? null : server.getName();
    }

    /** 可用性：HTTP → AVAILABLE；MCP → server 在线则 AVAILABLE 否则 UNAVAILABLE。 */
    private String availability(ToolEntity entity) {
        if (entity.getEnabled() == null || entity.getEnabled() != 1) {
            return "UNAVAILABLE";
        }
        if ("MCP".equals(entity.getSourceType())) {
            return entity.getMcpServerId() != null && connectionManager.isOnline(entity.getMcpServerId())
                    ? "AVAILABLE" : "UNAVAILABLE";
        }
        return "AVAILABLE";
    }

    private void validateName(String name, String excludeId) {
        if (name == null || name.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "name is required");
        }
        LambdaQueryWrapper<ToolEntity> wrapper = new LambdaQueryWrapper<ToolEntity>().eq(ToolEntity::getName, name);
        if (excludeId != null) {
            wrapper.ne(ToolEntity::getId, excludeId);
        }
        if (toolMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(ErrorCode.TOOL_NAME_DUPLICATE);
        }
    }

    private void requireHttpConfig(String method, String endpoint) {
        if (method == null || method.isBlank() || endpoint == null || endpoint.isBlank()) {
            throw new BusinessException(ErrorCode.TOOL_CONFIG_INVALID, "HTTP 工具需提供 method 与 endpoint");
        }
    }

    private ToolEntity require(String id) {
        ToolEntity entity = toolMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.TOOL_NOT_FOUND);
        }
        return entity;
    }
}
