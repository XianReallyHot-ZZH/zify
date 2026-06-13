package com.zify.model.infrastructure.client;

import com.zify.model.infrastructure.client.exception.LlmCancelledException;
import com.zify.model.infrastructure.client.exception.LlmException;
import com.zify.model.infrastructure.client.exception.LlmNonRetryableException;
import com.zify.model.infrastructure.client.exception.LlmTimeoutException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

/**
 * 显式 LLM 调用重试包装（不使用 @Retryable，glm-docs/07 §5）。
 * <p>
 * 最大 3 次尝试、初始退避 1s、倍率 2、jitter 20%、单次最大等待 10s、尊重总 deadline。
 * 可重试条件：异常 {@code isRetryable()} 且<b>尚未通过 sink 发送任何 delta</b>（流式只在首 chunk 前重试）。
 */
@Component
public class LlmRetryWrapper {

    public static final int MAX_ATTEMPTS = 3;
    public static final Duration INITIAL_BACKOFF = Duration.ofSeconds(1);
    public static final double MULTIPLIER = 2.0;
    public static final double JITTER = 0.20;
    public static final Duration MAX_BACKOFF = Duration.ofSeconds(10);

    /**
     * @param deltaSentSupplier 返回是否已通过 sink 发送过 delta；已发则不再重试
     */
    public <T> T withRetry(Callable<T> task, Supplier<Boolean> deltaSentSupplier, Instant deadline,
                           String providerId, String modelName, String scenario) {
        int attempt = 0;
        LlmException lastError = null;
        while (attempt < MAX_ATTEMPTS) {
            attempt++;
            Duration remaining = Duration.between(Instant.now(), deadline);
            if (remaining.isNegative() || remaining.isZero()) {
                throw new LlmTimeoutException("LLM call deadline exceeded before attempt " + attempt,
                        providerId, modelName, scenario, false, lastError);
            }
            try {
                return task.call();
            } catch (LlmException e) {
                lastError = e;
                boolean deltaSent = Boolean.TRUE.equals(deltaSentSupplier.get());
                boolean canRetry = e.isRetryable() && !deltaSent && attempt < MAX_ATTEMPTS;
                if (!canRetry) {
                    throw e;
                }
                Duration delay = computeBackoff(attempt);
                Duration untilDeadline = Duration.between(Instant.now(), deadline);
                if (delay.compareTo(untilDeadline) >= 0) {
                    throw new LlmTimeoutException("No time left for retry",
                            providerId, modelName, scenario, false, e);
                }
                sleep(delay);
            } catch (Exception e) {
                // 非 LlmException 的异常（含其它 RuntimeException）：保守按不可重试处理
                throw new LlmNonRetryableException("LLM call failed: " + e.getMessage(),
                        providerId, modelName, scenario, e);
            }
        }
        throw lastError;
    }

    private Duration computeBackoff(int attempt) {
        double base = INITIAL_BACKOFF.toMillis() * Math.pow(MULTIPLIER, attempt - 1);
        double jitterFactor = 1.0 + (Math.random() * 2.0 - 1.0) * JITTER; // 0.8 .. 1.2
        long delayMs = (long) (base * jitterFactor);
        long capped = Math.min(MAX_BACKOFF.toMillis(), delayMs);
        return Duration.ofMillis(Math.max(0, capped));
    }

    private void sleep(Duration delay) {
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmCancelledException("Interrupted during retry backoff",
                    null, null, "chat_stream", e);
        }
    }
}
