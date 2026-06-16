package com.zify.tool.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 工具调用配置（绑定 zify.tool），对齐 glm-docs/13 §四–§八。
 * <p>
 * 包含分层超时、per-tool_id 熔断、执行器并发、安全（SSRF / 响应截断 / 请求体上限）。
 */
@Component
@ConfigurationProperties(prefix = "zify.tool")
public class ToolProperties {

    private Timeout timeout = new Timeout();
    private CircuitBreaker circuitBreaker = new CircuitBreaker();
    private Executor executor = new Executor();
    private Security security = new Security();

    public Timeout getTimeout() {
        return timeout;
    }

    public void setTimeout(Timeout timeout) {
        this.timeout = timeout;
    }

    public CircuitBreaker getCircuitBreaker() {
        return circuitBreaker;
    }

    public void setCircuitBreaker(CircuitBreaker circuitBreaker) {
        this.circuitBreaker = circuitBreaker;
    }

    public Executor getExecutor() {
        return executor;
    }

    public void setExecutor(Executor executor) {
        this.executor = executor;
    }

    public Security getSecurity() {
        return security;
    }

    public void setSecurity(Security security) {
        this.security = security;
    }

    /** 工具调用超时（13 §4）。 */
    public static class Timeout {
        /** TCP/TLS 建连超时。 */
        private Duration connect = Duration.ofSeconds(10);
        /** MCP initialize 握手超时（含建连）。 */
        private Duration mcpHandshake = Duration.ofSeconds(15);
        /** 单次请求默认超时（tool.timeout_seconds 未设时兜底）。 */
        private Duration requestDefault = Duration.ofSeconds(30);
        /** 单次调用总超时上限（含重试），且不超过 ReAct 循环剩余时间。 */
        private Duration totalCap = Duration.ofSeconds(60);

        public Duration getConnect() {
            return connect;
        }

        public void setConnect(Duration connect) {
            this.connect = connect;
        }

        public Duration getMcpHandshake() {
            return mcpHandshake;
        }

        public void setMcpHandshake(Duration mcpHandshake) {
            this.mcpHandshake = mcpHandshake;
        }

        public Duration getRequestDefault() {
            return requestDefault;
        }

        public void setRequestDefault(Duration requestDefault) {
            this.requestDefault = requestDefault;
        }

        public Duration getTotalCap() {
            return totalCap;
        }

        public void setTotalCap(Duration totalCap) {
            this.totalCap = totalCap;
        }
    }

    /** per-tool_id 熔断（13 §6.1）。 */
    public static class CircuitBreaker {
        /** 连续可重试失败次数阈值。 */
        private int failureThreshold = 5;
        /** OPEN 持续时长。 */
        private Duration openDuration = Duration.ofSeconds(60);

        public int getFailureThreshold() {
            return failureThreshold;
        }

        public void setFailureThreshold(int failureThreshold) {
            this.failureThreshold = failureThreshold;
        }

        public Duration getOpenDuration() {
            return openDuration;
        }

        public void setOpenDuration(Duration openDuration) {
            this.openDuration = openDuration;
        }
    }

    /** 工具执行器（13 §3）。 */
    public static class Executor {
        /** 全局并发上限（全局 Semaphore，per-tool 限流留二期）。 */
        private int maxConcurrent = 50;

        public int getMaxConcurrent() {
            return maxConcurrent;
        }

        public void setMaxConcurrent(int maxConcurrent) {
            this.maxConcurrent = maxConcurrent;
        }
    }

    /** 安全（13 §8）。 */
    public static class Security {
        private Ssrf ssrf = new Ssrf();
        /** 响应截断阈值（字节）。 */
        private int responseMaxBytes = 32768;
        /** 请求体上限（字节）。 */
        private int requestMaxBytes = 1048576;

        public Ssrf getSsrf() {
            return ssrf;
        }

        public void setSsrf(Ssrf ssrf) {
            this.ssrf = ssrf;
        }

        public int getResponseMaxBytes() {
            return responseMaxBytes;
        }

        public void setResponseMaxBytes(int responseMaxBytes) {
            this.responseMaxBytes = responseMaxBytes;
        }

        public int getRequestMaxBytes() {
            return requestMaxBytes;
        }

        public void setRequestMaxBytes(int requestMaxBytes) {
            this.requestMaxBytes = requestMaxBytes;
        }
    }

    /** SSRF 防护（13 §8.1）。 */
    public static class Ssrf {
        /** 黑名单开关。 */
        private boolean enabled = true;
        /** 是否允许内网/保留地址（内网部署可开）。 */
        private boolean allowPrivate = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isAllowPrivate() {
            return allowPrivate;
        }

        public void setAllowPrivate(boolean allowPrivate) {
            this.allowPrivate = allowPrivate;
        }
    }
}
