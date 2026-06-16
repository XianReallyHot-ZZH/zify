package com.zify.tool.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 鉴权凭据明文 JSON 构造（加密前），区分 tool（§4.3，含 type）与 mcp server（§4.4，无 type）。
 * 返回 null 表示 NONE / 无凭据（不加密存储）。
 */
public final class AuthConfigs {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AuthConfigs() {
    }

    /** HTTP 工具 auth_config 明文（§4.3，含 type）。 */
    public static String forTool(String authType, String headerName, String credential) {
        return build(authType, headerName, credential, true);
    }

    /** MCP Server auth_config 明文（§4.4，无 type）。 */
    public static String forMcp(String authType, String headerName, String credential) {
        return build(authType, headerName, credential, false);
    }

    private static String build(String authType, String headerName, String credential, boolean includeType) {
        if (authType == null || "NONE".equals(authType) || credential == null || credential.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> m = new LinkedHashMap<>();
            if (includeType) {
                m.put("type", authType);
            }
            switch (authType) {
                case "API_KEY" -> {
                    m.put("headerName", headerName == null || headerName.isBlank() ? "X-Api-Key" : headerName);
                    m.put("apiKey", credential);
                }
                case "BEARER" -> m.put("token", credential);
                default -> {
                    return null;
                }
            }
            return MAPPER.writeValueAsString(m);
        } catch (Exception e) {
            return null;
        }
    }
}
