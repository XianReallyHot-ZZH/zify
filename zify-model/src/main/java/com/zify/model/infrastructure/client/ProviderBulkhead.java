package com.zify.model.infrastructure.client;

import com.zify.model.infrastructure.client.exception.LlmBusyException;
import com.zify.model.infrastructure.client.exception.LlmCancelledException;
import com.zify.model.infrastructure.client.exception.LlmException;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * 单个 Provider 的并发舱壁（bulkhead）：用 {@link Semaphore} 限制对该 Provider 的最大并发调用，
 * 避免把限流与连接压力直接打到模型服务（glm-docs/07 §3.5）。
 * <p>
 * 一个 Provider 一个实例，由 {@link LlmChatGateway} 按 providerId 缓存。
 */
public final class ProviderBulkhead {

    private final Semaphore semaphore;
    private final Duration acquireTimeout;
    private final String providerId;

    public ProviderBulkhead(int maxConcurrent, Duration acquireTimeout, String providerId) {
        this.semaphore = new Semaphore(maxConcurrent, true);
        this.acquireTimeout = acquireTimeout;
        this.providerId = providerId;
    }

    /**
     * 在并发许可内执行任务：拿不到许可（超时）抛 {@link LlmBusyException}，被中断抛
     * {@link LlmCancelledException}，其它异常原样上抛，finally 释放许可。
     */
    public <T> T execute(Callable<T> task, String modelName, String scenario) {
        boolean acquired;
        try {
            acquired = semaphore.tryAcquire(acquireTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmCancelledException("Interrupted while waiting for LLM provider permit",
                    providerId, modelName, scenario, e);
        }
        if (!acquired) {
            throw new LlmBusyException("LLM provider is busy (concurrency limit reached)",
                    providerId, modelName, scenario);
        }
        try {
            return task.call();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmException("LLM provider call failed", providerId, modelName, scenario, false, e);
        } finally {
            semaphore.release();
        }
    }
}
