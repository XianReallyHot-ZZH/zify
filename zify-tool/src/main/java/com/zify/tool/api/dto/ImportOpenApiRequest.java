package com.zify.tool.api.dto;

import com.zify.tool.domain.openapi.ImportSelection;

import java.util.List;

/**
 * OpenAPI 批量导入请求。
 */
public class ImportOpenApiRequest {

    /** 覆盖 spec server 的 baseUrl，可空。 */
    private String baseUrl;
    private String authType;
    private String authHeaderName;
    private String credential;
    private List<ImportSelection> operations;
    /** 原 spec 文本（后端复用解析）。 */
    private String spec;

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getAuthType() { return authType; }
    public void setAuthType(String authType) { this.authType = authType; }
    public String getAuthHeaderName() { return authHeaderName; }
    public void setAuthHeaderName(String authHeaderName) { this.authHeaderName = authHeaderName; }
    public String getCredential() { return credential; }
    public void setCredential(String credential) { this.credential = credential; }
    public List<ImportSelection> getOperations() { return operations; }
    public void setOperations(List<ImportSelection> operations) { this.operations = operations; }
    public String getSpec() { return spec; }
    public void setSpec(String spec) { this.spec = spec; }
}
