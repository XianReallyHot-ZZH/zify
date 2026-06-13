package com.zify.model.api.dto.chat;

/**
 * LLM 调用参数（均可空）。空字段由网关回退到 model.default_params。
 */
public class ChatOptions {

    private Double temperature;
    private Integer maxTokens;
    private Double topP;

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }

    public Double getTopP() {
        return topP;
    }

    public void setTopP(Double topP) {
        this.topP = topP;
    }
}
