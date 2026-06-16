package com.zify.tool.domain.openapi;

/**
 * OpenAPI 批量导入时的统一鉴权规格（应用到所有勾选工具的 headersTemplate）。
 * <p>
 * 实际凭据由 ToolService 加密落 auth_config；此处只决定 headersTemplate 里的占位头。
 */
public class AuthSpec {

    /** NONE / API_KEY / BEARER。 */
    private String type = "NONE";
    /** API_KEY 时的自定义 header 名。 */
    private String headerName;

    public AuthSpec() {
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getHeaderName() {
        return headerName;
    }

    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }
}
