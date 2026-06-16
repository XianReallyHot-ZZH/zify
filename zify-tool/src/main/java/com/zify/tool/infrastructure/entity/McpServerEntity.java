package com.zify.tool.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zify.common.persistence.entity.BaseEntity;

import java.time.LocalDateTime;

/**
 * MCP Server 连接配置实体（映射 mcp_server 表）。
 * <p>
 * auth_config 为加密 JSON 整体存储（复用 SecretEncryptor），映射为 String 密文。
 * active_name 是 generated column，不映射为字段（§3.1）。
 */
@TableName("mcp_server")
public class McpServerEntity extends BaseEntity {

    private String createdBy;
    private String updatedBy;
    private String name;
    private String description;
    private String baseUrl;
    private String transportType;
    private String authType;
    /** 加密后的认证凭据 JSON（密文），NONE 时为 null。明文仅连接/调用时解密。 */
    private String authConfig;
    private Integer enabled;
    /** 连接健康度：ONLINE / OFFLINE / ERROR（由连接生命周期驱动）。 */
    private String status;
    private String statusMessage;
    private LocalDateTime lastConnectedAt;

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getTransportType() {
        return transportType;
    }

    public void setTransportType(String transportType) {
        this.transportType = transportType;
    }

    public String getAuthType() {
        return authType;
    }

    public void setAuthType(String authType) {
        this.authType = authType;
    }

    public String getAuthConfig() {
        return authConfig;
    }

    public void setAuthConfig(String authConfig) {
        this.authConfig = authConfig;
    }

    public Integer getEnabled() {
        return enabled;
    }

    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public void setStatusMessage(String statusMessage) {
        this.statusMessage = statusMessage;
    }

    public LocalDateTime getLastConnectedAt() {
        return lastConnectedAt;
    }

    public void setLastConnectedAt(LocalDateTime lastConnectedAt) {
        this.lastConnectedAt = lastConnectedAt;
    }
}
