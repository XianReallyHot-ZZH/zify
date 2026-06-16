package com.zify.tool.infrastructure.converter;

import com.zify.common.persistence.id.IdGenerator;
import com.zify.tool.api.dto.CreateToolRequest;
import com.zify.tool.api.dto.ToolDetailResponse;
import com.zify.tool.api.dto.ToolSummaryResponse;
import com.zify.tool.api.dto.UpdateToolRequest;
import com.zify.tool.domain.openapi.ToolBuildPlan;
import com.zify.tool.infrastructure.entity.ToolEntity;

/**
 * Tool Entity <-> DTO 转换器（静态工具）。auth_config 密文不进 Response。
 */
public final class ToolConverter {

    private ToolConverter() {
    }

    public static ToolEntity toEntity(CreateToolRequest request, String authCipher, int idempotent) {
        ToolEntity entity = new ToolEntity();
        entity.setId(IdGenerator.uuid());
        entity.setName(request.getName());
        entity.setDescription(request.getDescription());
        entity.setSourceType("HTTP");
        entity.setInputSchema(request.getInputSchema());
        entity.setEndpoint(request.getEndpoint());
        entity.setMethod(request.getMethod() == null ? "GET" : request.getMethod().toUpperCase());
        entity.setConfigJson(request.getConfigJson());
        entity.setAuthConfig(authCipher);
        entity.setTimeoutSeconds(request.getTimeoutSeconds());
        entity.setIdempotent(idempotent);
        entity.setEnabled(1);
        return entity;
    }

    public static ToolEntity fromBuildPlan(ToolBuildPlan plan, String authCipher) {
        ToolEntity entity = new ToolEntity();
        entity.setId(IdGenerator.uuid());
        entity.setName(plan.getName());
        entity.setDescription(plan.getDescription());
        entity.setSourceType("HTTP");
        entity.setInputSchema(plan.getInputSchema());
        entity.setEndpoint(plan.getEndpoint());
        entity.setMethod(plan.getMethod() == null ? "GET" : plan.getMethod().toUpperCase());
        entity.setConfigJson(plan.getConfigJson());
        entity.setAuthConfig(authCipher);
        entity.setIdempotent(0); // 导入默认非幂等（保守）
        entity.setEnabled(1);
        return entity;
    }

    public static void applyUpdate(ToolEntity entity, UpdateToolRequest request, String authCipher, Integer idempotent) {
        if (request.getName() != null) {
            entity.setName(request.getName());
        }
        if (request.getDescription() != null) {
            entity.setDescription(request.getDescription());
        }
        if (request.getMethod() != null) {
            entity.setMethod(request.getMethod().toUpperCase());
        }
        if (request.getEndpoint() != null) {
            entity.setEndpoint(request.getEndpoint());
        }
        if (request.getInputSchema() != null) {
            entity.setInputSchema(request.getInputSchema());
        }
        if (request.getConfigJson() != null) {
            entity.setConfigJson(request.getConfigJson());
        }
        if (authCipher != null) {
            entity.setAuthConfig(authCipher);
        }
        if (request.getTimeoutSeconds() != null) {
            entity.setTimeoutSeconds(request.getTimeoutSeconds());
        }
        if (idempotent != null) {
            entity.setIdempotent(idempotent);
        }
        if (request.getEnabled() != null) {
            entity.setEnabled(request.getEnabled());
        }
    }

    public static ToolSummaryResponse toSummary(ToolEntity entity, String mcpServerName, String status) {
        ToolSummaryResponse r = new ToolSummaryResponse();
        r.setId(entity.getId());
        r.setName(entity.getName());
        r.setDescription(entity.getDescription());
        r.setSourceType(entity.getSourceType());
        r.setMcpServerId(entity.getMcpServerId());
        r.setMcpServerName(mcpServerName);
        r.setEnabled(entity.getEnabled());
        r.setMethod(entity.getMethod());
        r.setEndpoint(entity.getEndpoint());
        r.setStatus(status);
        r.setCreatedAt(entity.getCreatedAt());
        return r;
    }

    public static ToolDetailResponse toDetail(ToolEntity entity, String mcpServerName, String status,
                                              String authType) {
        ToolDetailResponse r = new ToolDetailResponse();
        r.setId(entity.getId());
        r.setName(entity.getName());
        r.setDescription(entity.getDescription());
        r.setSourceType(entity.getSourceType());
        r.setMcpServerId(entity.getMcpServerId());
        r.setMcpServerName(mcpServerName);
        r.setInputSchema(entity.getInputSchema());
        r.setEndpoint(entity.getEndpoint());
        r.setMethod(entity.getMethod());
        r.setConfigJson(entity.getConfigJson());
        r.setAuthType(authType);
        r.setHasAuth(hasAuth(entity));
        r.setTimeoutSeconds(entity.getTimeoutSeconds());
        r.setIdempotent(entity.getIdempotent());
        r.setEnabled(entity.getEnabled());
        r.setStatus(status);
        r.setCreatedAt(entity.getCreatedAt());
        return r;
    }

    public static boolean hasAuth(ToolEntity entity) {
        return entity.getAuthConfig() != null && !entity.getAuthConfig().isBlank();
    }
}
