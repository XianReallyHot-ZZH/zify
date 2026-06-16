package com.zify.tool.infrastructure.client;

import com.zify.common.security.SecretEncryptor;
import com.zify.tool.config.ToolProperties;
import com.zify.tool.infrastructure.client.breaker.ToolCircuitBreaker;
import com.zify.tool.infrastructure.client.retry.ToolRetryWrapper;
import com.zify.tool.infrastructure.client.sizer.ResponseSizer;
import com.zify.tool.infrastructure.client.ssrf.SsrfGuard;
import com.zify.tool.infrastructure.mapper.ToolCallLogMapper;
import org.springframework.stereotype.Component;

/**
 * 工具执行共享运行时组件（glm-docs/13 §四–§八）。
 * <p>
 * 聚合 HttpTool / McpTool 共用的保护组件 + 加密器 + 日志 Mapper + 配置，
 * 供按执行点构造的 Tool 实现注入，避免 10 参构造器爆炸。
 */
@Component
public class ToolExecSupport {

    private final SsrfGuard ssrfGuard;
    private final ResponseSizer responseSizer;
    private final ToolCircuitBreaker circuitBreaker;
    private final ToolRetryWrapper retryWrapper;
    private final SecretEncryptor secretEncryptor;
    private final ToolCallLogMapper toolCallLogMapper;
    private final ToolProperties properties;

    public ToolExecSupport(SsrfGuard ssrfGuard, ResponseSizer responseSizer, ToolCircuitBreaker circuitBreaker,
                           ToolRetryWrapper retryWrapper, SecretEncryptor secretEncryptor,
                           ToolCallLogMapper toolCallLogMapper, ToolProperties properties) {
        this.ssrfGuard = ssrfGuard;
        this.responseSizer = responseSizer;
        this.circuitBreaker = circuitBreaker;
        this.retryWrapper = retryWrapper;
        this.secretEncryptor = secretEncryptor;
        this.toolCallLogMapper = toolCallLogMapper;
        this.properties = properties;
    }

    public SsrfGuard getSsrfGuard() {
        return ssrfGuard;
    }

    public ResponseSizer getResponseSizer() {
        return responseSizer;
    }

    public ToolCircuitBreaker getCircuitBreaker() {
        return circuitBreaker;
    }

    public ToolRetryWrapper getRetryWrapper() {
        return retryWrapper;
    }

    public SecretEncryptor getSecretEncryptor() {
        return secretEncryptor;
    }

    public ToolCallLogMapper getToolCallLogMapper() {
        return toolCallLogMapper;
    }

    public ToolProperties getProperties() {
        return properties;
    }
}
