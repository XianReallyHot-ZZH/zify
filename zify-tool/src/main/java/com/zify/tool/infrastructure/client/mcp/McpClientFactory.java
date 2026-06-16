package com.zify.tool.infrastructure.client.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zify.common.security.SecretEncryptor;
import com.zify.tool.config.ToolProperties;
import com.zify.tool.infrastructure.entity.McpServerEntity;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpClientTransport;
import org.springframework.stereotype.Component;

import java.net.http.HttpRequest;
import java.util.Map;

/**
 * 程序化构造 {@link McpSyncClient}（glm-docs/13 §9.2 / P2 §七）。
 * <p>
 * 按 transportType 选 {@link HttpClientStreamableHttpTransport} / {@link HttpClientSseClientTransport}，
 * 注入认证 Header（API_KEY→自定义 header / BEARER→Authorization，从 auth_config 解密），
 * requestTimeout/initializationTimeout 对齐 ToolProperties.timeout。
 * <b>凭据仅此栈内，不入日志。</b>
 */
@Component
public class McpClientFactory {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ToolProperties properties;
    private final SecretEncryptor secretEncryptor;

    public McpClientFactory(ToolProperties properties, SecretEncryptor secretEncryptor) {
        this.properties = properties;
        this.secretEncryptor = secretEncryptor;
    }

    /**
     * 按 server 配置构造未初始化的 McpSyncClient（调用方负责 initialize/close）。
     */
    public McpSyncClient build(McpServerEntity server) {
        String baseUrl = server.getBaseUrl();
        String transportType = server.getTransportType() == null ? "STREAMABLE_HTTP" : server.getTransportType();
        String authType = server.getAuthType() == null ? "NONE" : server.getAuthType();
        String plainAuth = decrypt(server.getAuthConfig());

        HttpRequest.Builder requestBuilder = authRequestBuilder(authType, plainAuth);
        McpClientTransport transport = buildTransport(transportType, baseUrl, requestBuilder);

        return McpClient.sync(transport)
                .requestTimeout(properties.getTimeout().getRequestDefault())
                .initializationTimeout(properties.getTimeout().getMcpHandshake())
                .build();
    }

    /**
     * 测试连接（不持久化连接/工具）：建连 → initialize → listTools → 关闭。
     * 成功返回发现的工具，失败返回 message（不抛）。initialize 受 mcp-handshake 超时约束。
     */
    public McpTestResult test(McpServerEntity server) {
        McpSyncClient client = build(server);
        try {
            client.initialize();
            io.modelcontextprotocol.spec.McpSchema.ListToolsResult result = client.listTools();
            java.util.List<io.modelcontextprotocol.spec.McpSchema.Tool> tools =
                    result == null || result.tools() == null ? java.util.List.of() : result.tools();
            return new McpTestResult(true, null, tools);
        } catch (Exception e) {
            return new McpTestResult(false, brief(e), java.util.List.of());
        } finally {
            try {
                client.close();
            } catch (Exception ignored) {
                // 关闭异常忽略
            }
        }
    }

    /** 测试连接结果。 */
    public static final class McpTestResult {
        private final boolean success;
        private final String message;
        private final java.util.List<io.modelcontextprotocol.spec.McpSchema.Tool> tools;

        public McpTestResult(boolean success, String message,
                             java.util.List<io.modelcontextprotocol.spec.McpSchema.Tool> tools) {
            this.success = success;
            this.message = message;
            this.tools = tools;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public java.util.List<io.modelcontextprotocol.spec.McpSchema.Tool> getTools() {
            return tools;
        }
    }

    private static String brief(Throwable e) {
        String msg = e.getMessage();
        return msg == null ? e.getClass().getSimpleName() : (msg.length() > 240 ? msg.substring(0, 240) : msg);
    }

    private McpClientTransport buildTransport(String transportType, String baseUrl, HttpRequest.Builder requestBuilder) {
        if ("SSE".equalsIgnoreCase(transportType)) {
            HttpClientSseClientTransport.Builder b = HttpClientSseClientTransport.builder(baseUrl)
                    .connectTimeout(properties.getTimeout().getConnect());
            b.requestBuilder(requestBuilder);
            return b.build();
        }
        // 默认 STREAMABLE_HTTP
        HttpClientStreamableHttpTransport.Builder b = HttpClientStreamableHttpTransport.builder(baseUrl)
                .connectTimeout(properties.getTimeout().getConnect());
        b.requestBuilder(requestBuilder);
        return b.build();
    }

    @SuppressWarnings("unchecked")
    private HttpRequest.Builder authRequestBuilder(String authType, String plainAuth) {
        HttpRequest.Builder rb = HttpRequest.newBuilder();
        if ("NONE".equals(authType) || plainAuth == null || plainAuth.isBlank()) {
            return rb;
        }
        try {
            Map<String, Object> auth = MAPPER.readValue(plainAuth, Map.class);
            if ("API_KEY".equals(authType)) {
                rb.header(str(auth.get("headerName"), "X-Api-Key"), str(auth.get("apiKey")));
            } else if ("BEARER".equals(authType)) {
                rb.header("Authorization", "Bearer " + str(auth.get("token")));
            }
        } catch (Exception ignored) {
            // 解析失败：不加认证头（连接会被对端拒绝，置 ERROR）
        }
        return rb;
    }

    private String decrypt(String cipher) {
        if (cipher == null || cipher.isBlank()) {
            return null;
        }
        try {
            return secretEncryptor.decrypt(cipher);
        } catch (Exception e) {
            return null;
        }
    }

    private static String str(Object o, String def) {
        Object v = o == null ? def : o;
        return v == null ? "" : String.valueOf(v);
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}
