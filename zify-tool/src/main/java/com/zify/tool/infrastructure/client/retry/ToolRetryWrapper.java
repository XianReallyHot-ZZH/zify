package com.zify.tool.infrastructure.client.retry;

import com.zify.tool.infrastructure.exception.ToolCancelledException;
import com.zify.tool.infrastructure.exception.ToolException;
import com.zify.tool.infrastructure.exception.ToolNonRetryableException;
import com.zify.tool.infrastructure.exception.ToolRetryableException;
import com.zify.tool.infrastructure.exception.ToolTimeoutException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Callable;

/**
 * 显式工具调用重试包装（不使用 @Retryable，glm-docs/13 §5）。
 * <p>
 * 最大 3 次尝试、初始退避 1s、倍率 2、jitter 20%、单次最大等待 10s、尊重总 deadline。
 * 可重试条件（幂等性驱动，13 §5.2）：
 * <ul>
 *   <li>建连失败（{@code sent=false}）→ 幂等/非幂等均可重试（请求未送达）。</li>
 *   <li>请求已发出后失败（读超时/5xx/429，{@code sent=true}）→ 仅 {@code idempotent=true} 可重试。</li>
 *   <li>4xx / 不可重试 → 不重试。</li>
 * </ul>
 * 抛 {@link ToolRetryableException} 触发重试；耗尽 → 抛 {@link ToolRetryableException}（由上层转 ERROR 回灌）。
 */
@Component
public class ToolRetryWrapper {

    public static final int MAX_ATTEMPTS = 3;
    public static final Duration INITIAL_BACKOFF = Duration.ofSeconds(1);
    public static final double MULTIPLIER = 2.0;
    public static final double JITTER = 0.20;
    public static final Duration MAX_BACKOFF = Duration.ofSeconds(10);

    public <T> T withRetry(boolean idempotent, Instant deadline, Callable<T> action,
                           String toolId, String toolName, String scenario) {
        int attempt = 0;
        ToolException lastError = null;
        while (attempt < MAX_ATTEMPTS) {
            attempt++;
            Duration remaining = Duration.between(Instant.now(), deadline);
            if (remaining.isNegative() || remaining.isZero()) {
                throw new ToolTimeoutException("tool call deadline exceeded before attempt " + attempt,
                        toolId, toolName, scenario, lastError);
            }
            try {
                return action.call();
            } catch (ToolException e) {
                lastError = e;
                boolean sent = e.isSent();
                boolean canRetry = e.isRetryable() && (!sent || idempotent) && attempt < MAX_ATTEMPTS;
                if (!canRetry) {
                    // 请求已发出后失败且非幂等 → 转不可重试，避免重复副作用（13 §5.2）
                    if (e.isRetryable() && sent && !idempotent) {
                        throw new ToolNonRetryableException(
                                "non-idempotent tool failed after request sent: " + e.getMessage(),
                                toolId, toolName, scenario, e);
                    }
                    throw e;
                }
                Duration delay = computeBackoff(attempt);
                Duration untilDeadline = Duration.between(Instant.now(), deadline);
                if (delay.compareTo(untilDeadline) >= 0) {
                    throw new ToolTimeoutException("no time left for tool retry",
                            toolId, toolName, scenario, e);
                }
                sleep(delay, toolId, toolName, scenario);
            } catch (Exception e) {
                // 非 ToolException：保守按不可重试处理
                throw new ToolNonRetryableException("tool call failed: " + e.getMessage(),
                        toolId, toolName, scenario, e);
            }
        }
        // 重试耗尽 → 抛可重试异常（由上层转 status=ERROR 回灌）
        boolean sent = lastError != null && lastError.isSent();
        throw new ToolRetryableException("tool call exhausted retries: "
                + (lastError != null ? lastError.getMessage() : "unknown"),
                toolId, toolName, scenario, sent);
    }

    private Duration computeBackoff(int attempt) {
        double base = INITIAL_BACKOFF.toMillis() * Math.pow(MULTIPLIER, attempt - 1);
        double jitterFactor = 1.0 + (Math.random() * 2.0 - 1.0) * JITTER; // 0.8 .. 1.2
        long delayMs = (long) (base * jitterFactor);
        long capped = Math.min(MAX_BACKOFF.toMillis(), delayMs);
        return Duration.ofMillis(Math.max(0, capped));
    }

    private void sleep(Duration delay, String toolId, String toolName, String scenario) {
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ToolCancelledException("interrupted during tool retry backoff",
                    toolId, toolName, scenario, e);
        }
    }
}
