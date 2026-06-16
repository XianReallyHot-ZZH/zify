package com.zify.engine.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * ReAct 多轮循环控制配置（绑定 zify.chat.react），对齐 glm-docs/13 §C4 / P2 功能规格 §8.2。
 * <p>
 * 放 engine：ReAct 循环在 engine 驱动，循环控制由此配置；前缀沿用 zify.chat.react
 * （属于 chat 功能的 ReAct 配置，与 ChatContextProperties 同属 chat 配置族）。
 */
@Component
@ConfigurationProperties(prefix = "zify.chat.react")
public class ReactLoopProperties {

    /** 最大轮次（达此值正常截断，finishReason=MAX_TURNS，不报错）。 */
    private int maxTurns = 10;
    /** 整轮循环 deadline（从用户发消息起）。 */
    private Duration loopDeadline = Duration.ofSeconds(120);
    /** 同一 (toolName, args) 连续重复次数阈值（达此值先回灌提示，仍重复则中断）。 */
    private int dupToolCallThreshold = 3;

    public int getMaxTurns() {
        return maxTurns;
    }

    public void setMaxTurns(int maxTurns) {
        this.maxTurns = maxTurns;
    }

    public Duration getLoopDeadline() {
        return loopDeadline;
    }

    public void setLoopDeadline(Duration loopDeadline) {
        this.loopDeadline = loopDeadline;
    }

    public int getDupToolCallThreshold() {
        return dupToolCallThreshold;
    }

    public void setDupToolCallThreshold(int dupToolCallThreshold) {
        this.dupToolCallThreshold = dupToolCallThreshold;
    }
}
