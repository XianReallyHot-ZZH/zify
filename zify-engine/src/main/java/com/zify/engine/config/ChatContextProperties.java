package com.zify.engine.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 对话上下文管理配置（绑定 zify.chat.context）。
 * <p>
 * 放 engine（chat 依赖 engine 复用）：ContextManager 预算估算 + chat 单条消息上限共用。
 * 前缀沿用 zify.chat.context（属于 chat 功能的上下文配置）。
 */
@Component
@ConfigurationProperties(prefix = "zify.chat.context")
public class ChatContextProperties {

    /** model.context_window 为空时的兜底窗口（token）。 */
    private int defaultWindow = 128000;
    /** 触发摘要压缩的预算占比。 */
    private double budgetThreshold = 0.75;
    /** 单次压缩折叠的消息条数。 */
    private int compactionBatch = 6;
    /** 单条用户消息上限（估算 token），超则拒绝。 */
    private int maxInputTokens = 30000;
    /** 为 summary 预留的预算（token）。 */
    private int summaryOverheadTokens = 2000;

    public int getDefaultWindow() {
        return defaultWindow;
    }

    public void setDefaultWindow(int defaultWindow) {
        this.defaultWindow = defaultWindow;
    }

    public double getBudgetThreshold() {
        return budgetThreshold;
    }

    public void setBudgetThreshold(double budgetThreshold) {
        this.budgetThreshold = budgetThreshold;
    }

    public int getCompactionBatch() {
        return compactionBatch;
    }

    public void setCompactionBatch(int compactionBatch) {
        this.compactionBatch = compactionBatch;
    }

    public int getMaxInputTokens() {
        return maxInputTokens;
    }

    public void setMaxInputTokens(int maxInputTokens) {
        this.maxInputTokens = maxInputTokens;
    }

    public int getSummaryOverheadTokens() {
        return summaryOverheadTokens;
    }

    public void setSummaryOverheadTokens(int summaryOverheadTokens) {
        this.summaryOverheadTokens = summaryOverheadTokens;
    }
}
