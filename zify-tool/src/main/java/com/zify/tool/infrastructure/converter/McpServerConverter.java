package com.zify.tool.infrastructure.converter;

import com.zify.common.persistence.id.IdGenerator;
import com.zify.tool.api.dto.CreateMcpServerRequest;
import com.zify.tool.api.dto.McpDiscoveredToolResponse;
import com.zify.tool.api.dto.McpServerDetailResponse;
import com.zify.tool.api.dto.McpServerSummaryResponse;
import com.zify.tool.api.dto.UpdateMcpServerRequest;
import com.zify.tool.infrastructure.entity.McpServerEntity;

import java.util.List;

/**
 * McpServer Entity <-> DTO 转换器（静态工具）。auth_config 密文不进 Response。
 */
public final class McpServerConverter {

    public static final String DEFAULT_TRANSPORT = "STREAMABLE_HTTP";
    public static final String DEFAULT_AUTH = "NONE";

    private McpServerConverter() {
    }

    public static McpServerEntity toEntity(CreateMcpServerRequest request, String authCipher) {
        McpServerEntity entity = new McpServerEntity();
        entity.setId(IdGenerator.uuid());
        entity.setName(request.getName());
        entity.setDescription(request.getDescription());
        entity.setBaseUrl(request.getBaseUrl());
        entity.setTransportType(request.getTransportType() == null ? DEFAULT_TRANSPORT : request.getTransportType());
        entity.setAuthType(request.getAuthType() == null ? DEFAULT_AUTH : request.getAuthType());
        entity.setAuthConfig(authCipher);
        entity.setEnabled(1);
        entity.setStatus("OFFLINE");
        return entity;
    }

    /**
     * 部分更新；authCipher 非 null 时覆盖凭据（credential 留空 → null → 不改）。
     */
    public static void applyUpdate(McpServerEntity entity, UpdateMcpServerRequest request, String authCipher) {
        if (request.getName() != null) {
            entity.setName(request.getName());
        }
        if (request.getDescription() != null) {
            entity.setDescription(request.getDescription());
        }
        if (request.getBaseUrl() != null) {
            entity.setBaseUrl(request.getBaseUrl());
        }
        if (request.getTransportType() != null) {
            entity.setTransportType(request.getTransportType());
        }
        if (request.getAuthType() != null) {
            entity.setAuthType(request.getAuthType());
        }
        if (authCipher != null) {
            entity.setAuthConfig(authCipher);
        }
        if (request.getEnabled() != null) {
            entity.setEnabled(request.getEnabled());
        }
    }

    public static McpServerSummaryResponse toSummary(McpServerEntity entity, long toolsCount) {
        McpServerSummaryResponse r = new McpServerSummaryResponse();
        fillSummary(r, entity);
        r.setToolsCount(toolsCount);
        return r;
    }

    public static McpServerDetailResponse toDetail(McpServerEntity entity, List<McpDiscoveredToolResponse> tools) {
        McpServerDetailResponse r = new McpServerDetailResponse();
        r.setId(entity.getId());
        r.setName(entity.getName());
        r.setDescription(entity.getDescription());
        r.setBaseUrl(entity.getBaseUrl());
        r.setTransportType(entity.getTransportType());
        r.setAuthType(entity.getAuthType());
        r.setHasAuth(hasAuth(entity));
        r.setEnabled(entity.getEnabled());
        r.setStatus(entity.getStatus());
        r.setStatusMessage(entity.getStatusMessage());
        r.setLastConnectedAt(entity.getLastConnectedAt());
        r.setCreatedAt(entity.getCreatedAt());
        r.setDiscoveredTools(tools);
        return r;
    }

    public static boolean hasAuth(McpServerEntity entity) {
        return entity.getAuthConfig() != null && !entity.getAuthConfig().isBlank();
    }

    private static void fillSummary(McpServerSummaryResponse r, McpServerEntity entity) {
        r.setId(entity.getId());
        r.setName(entity.getName());
        r.setDescription(entity.getDescription());
        r.setBaseUrl(entity.getBaseUrl());
        r.setTransportType(entity.getTransportType());
        r.setAuthType(entity.getAuthType());
        r.setHasAuth(hasAuth(entity));
        r.setEnabled(entity.getEnabled());
        r.setStatus(entity.getStatus());
        r.setCreatedAt(entity.getCreatedAt());
    }
}
