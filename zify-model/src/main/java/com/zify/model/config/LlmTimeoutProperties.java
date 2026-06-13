package com.zify.model.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * LLM 流式调用的超时配置（绑定 zify.llm.timeout）。
 * <p>
 * 对齐 glm-docs/07 §4：连接超时、首 token 超时、idle 超时、总 deadline 分开控制。
 */
@Component
@ConfigurationProperties(prefix = "zify.llm.timeout")
public class LlmTimeoutProperties {

    private ChatStream chatStream = new ChatStream();

    public ChatStream getChatStream() {
        return chatStream;
    }

    public void setChatStream(ChatStream chatStream) {
        this.chatStream = chatStream;
    }

    /**
     * chat 流式调用的超时配置。
     */
    public static class ChatStream {

        /** TCP/TLS 建连超时。 */
        private Duration connect = Duration.ofSeconds(10);

        /** 从发出请求到收到第一个 chunk 的超时（可重试）。 */
        private Duration firstToken = Duration.ofSeconds(30);

        /** 两个 chunk 之间的最大等待时间（首 chunk 后触发，不自动重试）。 */
        private Duration idle = Duration.ofSeconds(45);

        /** 从发起调用到结束的总 deadline。 */
        private Duration total = Duration.ofSeconds(120);

        public Duration getConnect() {
            return connect;
        }

        public void setConnect(Duration connect) {
            this.connect = connect;
        }

        public Duration getFirstToken() {
            return firstToken;
        }

        public void setFirstToken(Duration firstToken) {
            this.firstToken = firstToken;
        }

        public Duration getIdle() {
            return idle;
        }

        public void setIdle(Duration idle) {
            this.idle = idle;
        }

        public Duration getTotal() {
            return total;
        }

        public void setTotal(Duration total) {
            this.total = total;
        }
    }
}
