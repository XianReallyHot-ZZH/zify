package com.zify.tool.infrastructure.client.breaker;

import com.zify.tool.config.ToolProperties;
import com.zify.tool.infrastructure.exception.ToolCircuitOpenException;
import com.zify.tool.infrastructure.exception.ToolException;
import com.zify.tool.infrastructure.exception.ToolNonRetryableException;
import com.zify.tool.infrastructure.exception.ToolRetryableException;
import org.springframework.stereotype.Component;

import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内、per {@code tool_id} 熔断器（glm-docs/13 §6.1）。
 * <p>
 * CLOSED → 连续 {@code failureThreshold} 次可重试失败 → OPEN {@code openDuration}
 * → HALF_OPEN 放 1 个探测；探测成功 → CLOSED，探测可重试失败 → 重新 OPEN。
 * 4xx/不可重试失败不计入熔断失败次数。
 */
@Component
public class ToolCircuitBreaker {

    private final ToolProperties properties;
    private final ConcurrentHashMap<String, BreakerState> states = new ConcurrentHashMap<>();

    public ToolCircuitBreaker(ToolProperties properties) {
        this.properties = properties;
    }

    /**
     * 在熔断保护下执行 action。OPEN 时抛 {@link ToolCircuitOpenException}。
     */
    public <T> T execute(String toolId, String toolName, String scenario, Callable<T> action) {
        BreakerState st = states.computeIfAbsent(toolId, k -> new BreakerState());
        long openNanos = properties.getCircuitBreaker().getOpenDuration().toNanos();
        int threshold = properties.getCircuitBreaker().getFailureThreshold();

        boolean allowed;
        synchronized (st) {
            allowed = st.beforeCall(openNanos, System.nanoTime());
        }
        if (!allowed) {
            throw new ToolCircuitOpenException("circuit open for tool " + toolName,
                    toolId, toolName, scenario);
        }
        try {
            T result = action.call();
            st.onSuccess();
            return result;
        } catch (ToolException e) {
            synchronized (st) {
                if (e.isRetryable()) {
                    st.onRetryableFailure(threshold, System.nanoTime());
                } else {
                    st.onNonRetryableFailure();
                }
            }
            throw e;
        } catch (Exception e) {
            // 未知异常按可重试失败计入
            synchronized (st) {
                st.onRetryableFailure(threshold, System.nanoTime());
            }
            throw new ToolRetryableException("tool call failed: " + e.getMessage(),
                    toolId, toolName, scenario, e);
        }
    }

    /** 单个 tool_id 的熔断状态机。 */
    private static final class BreakerState {

        private enum Phase { CLOSED, OPEN, HALF_OPEN }

        private Phase phase = Phase.CLOSED;
        private int failures = 0;
        private long openedAtNanos = 0L;
        private boolean halfOpenProbeInFlight = false;

        /** 返回是否允许本次调用（HALF_OPEN 只放一个探测）。 */
        synchronized boolean beforeCall(long openNanos, long nowNanos) {
            switch (phase) {
                case OPEN:
                    if (nowNanos - openedAtNanos >= openNanos) {
                        phase = Phase.HALF_OPEN;
                        halfOpenProbeInFlight = true;
                        return true; // 探测放行
                    }
                    return false;
                case HALF_OPEN:
                    if (halfOpenProbeInFlight) {
                        return false;
                    }
                    halfOpenProbeInFlight = true;
                    return true;
                default:
                    return true;
            }
        }

        synchronized void onSuccess() {
            phase = Phase.CLOSED;
            failures = 0;
            halfOpenProbeInFlight = false;
        }

        synchronized void onRetryableFailure(int threshold, long nowNanos) {
            halfOpenProbeInFlight = false;
            if (phase == Phase.HALF_OPEN) {
                phase = Phase.OPEN;
                openedAtNanos = nowNanos;
                return;
            }
            failures++;
            if (failures >= threshold) {
                phase = Phase.OPEN;
                openedAtNanos = nowNanos;
            }
        }

        /** 不可重试失败（4xx 等）：不计入失败次数；HALF_OPEN 时探测的服务能响应即视为恢复。 */
        synchronized void onNonRetryableFailure() {
            halfOpenProbeInFlight = false;
            if (phase == Phase.HALF_OPEN) {
                phase = Phase.CLOSED;
                failures = 0;
            }
        }
    }
}
