package com.zify.tool.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.zify.common.persistence.entity.BaseEntity;

import java.util.Map;

/**
 * 统一工具定义实体（映射 tool 表）。HTTP 工具 + MCP 工具（P4 再加 Workflow-as-Tool）。
 * <p>
 * config_json 为 Map（JacksonTypeHandler），故 autoResultMap=true；auth_config 为加密 JSON
 * 整体存储（密文 String）。active_name 是 generated column，不映射为字段（§3.2）。
 */
@TableName(value = "tool", autoResultMap = true)
public class ToolEntity extends BaseEntity {

    private String createdBy;
    private String updatedBy;
    private String name;
    private String description;
    /** 工具来源：HTTP / MCP / WORKFLOW（创建后不可改）。 */
    private String sourceType;
    private String mcpServerId;
    /** 工具输入参数 JSON Schema（字符串），定义时一次生成，运行时原样透传给 LLM。 */
    private String inputSchema;

    /** HTTP 工具完整请求地址（含 {param} 占位；仅 HTTP）。 */
    private String endpoint;
    /** HTTP 方法（仅 HTTP）。 */
    private String method;
    /** HTTP 工具配置：params_mapping/headers_template/body_template（仅 HTTP）。 */
    @TableField(value = "config_json", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> configJson;
    /** HTTP 工具鉴权凭据（加密 JSON 密文；仅 HTTP，NONE 时 null）。 */
    private String authConfig;

    private Integer timeoutSeconds;
    private Integer idempotent;
    private Integer enabled;

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

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getMcpServerId() {
        return mcpServerId;
    }

    public void setMcpServerId(String mcpServerId) {
        this.mcpServerId = mcpServerId;
    }

    public String getInputSchema() {
        return inputSchema;
    }

    public void setInputSchema(String inputSchema) {
        this.inputSchema = inputSchema;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public Map<String, Object> getConfigJson() {
        return configJson;
    }

    public void setConfigJson(Map<String, Object> configJson) {
        this.configJson = configJson;
    }

    public String getAuthConfig() {
        return authConfig;
    }

    public void setAuthConfig(String authConfig) {
        this.authConfig = authConfig;
    }

    public Integer getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(Integer timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public Integer getIdempotent() {
        return idempotent;
    }

    public void setIdempotent(Integer idempotent) {
        this.idempotent = idempotent;
    }

    public Integer getEnabled() {
        return enabled;
    }

    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
    }
}
