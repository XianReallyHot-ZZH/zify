package com.zify.tool.infrastructure.executor;

import com.zify.tool.config.ToolProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

/**
 * 工具执行器配置（glm-docs/13 §3 / P2 §三）。
 * <p>
 * 独立虚拟线程执行器 {@code toolExecutor}（与 llmTaskExecutor 隔离）+ 全局 {@code toolSemaphore}
 * （max-concurrent，防止工具调用风暴打爆外部服务）。newVirtualThreadPerTaskExecutor 是 07 §3.2
 * 已定例外（CLAUDE.md §3 禁 Executors.newXxx 针对 newFixed/newCached 的无界队列 OOM）。
 * 同时启用 @Scheduled（MCP 断连重连，13 §9.3 / P2 §七）。
 */
@Configuration
@EnableScheduling
public class ToolExecutorConfig {

    @Bean(name = "toolExecutor", destroyMethod = "close")
    public ExecutorService toolExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * 全局工具并发许可（fair），调用工具前 acquire，防止调用风暴。
     */
    @Bean(name = "toolSemaphore")
    public Semaphore toolSemaphore(ToolProperties toolProperties) {
        int maxConcurrent = Math.max(1, toolProperties.getExecutor().getMaxConcurrent());
        return new Semaphore(maxConcurrent, true);
    }
}
