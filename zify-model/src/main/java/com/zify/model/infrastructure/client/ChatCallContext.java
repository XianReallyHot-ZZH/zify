package com.zify.model.infrastructure.client;

import com.zify.model.api.dto.chat.ChatMessage;
import com.zify.model.api.dto.chat.ChatOptions;
import com.zify.model.api.dto.chat.ToolDefinitionDTO;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 单次 LLM 流式调用的上下文（client 包内）。
 * <p>
 * apiKey 为<b>明文</b>，仅在解密后到 client 调用之间短暂存在，禁止记录或入异常。
 */
public class ChatCallContext {

    /** 供应商类型：OPENAI / OPENAI_COMPATIBLE / ANTHROPIC */
    private final String providerType;
    /** API Base URL */
    private final String baseUrl;
    /** 明文 API Key（可空，如 Ollama） */
    private final String apiKey;
    /** 模型标识（发给 API 的 model 名） */
    private final String modelName;
    /** 供应商特有配置（如 anthropic apiVersion） */
    private final Map<String, Object> extraConfig;
    /** 供应商 ID（仅用于日志/异常） */
    private final String providerId;
    /** 消息序列（已含 system + 历史 + 本轮 user） */
    private final List<ChatMessage> messages;
    /** 调用参数（已与 model.default_params 合并） */
    private final ChatOptions options;
    /** 本次调用总 deadline */
    private final Instant deadline;
    /** 调用场景（chat_stream / summary 等），仅用于日志/异常 */
    private final String scenario;
    /** 下发的工具定义（可空；P2 工具调用） */
    private final List<ToolDefinitionDTO> toolDefinitions;

    public ChatCallContext(String providerType, String baseUrl, String apiKey, String modelName,
                           Map<String, Object> extraConfig, String providerId,
                           List<ChatMessage> messages, ChatOptions options, Instant deadline, String scenario) {
        this(providerType, baseUrl, apiKey, modelName, extraConfig, providerId,
                messages, options, deadline, scenario, null);
    }

    public ChatCallContext(String providerType, String baseUrl, String apiKey, String modelName,
                           Map<String, Object> extraConfig, String providerId,
                           List<ChatMessage> messages, ChatOptions options, Instant deadline, String scenario,
                           List<ToolDefinitionDTO> toolDefinitions) {
        this.providerType = providerType;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.modelName = modelName;
        this.extraConfig = extraConfig;
        this.providerId = providerId;
        this.messages = messages;
        this.options = options;
        this.deadline = deadline;
        this.scenario = scenario;
        this.toolDefinitions = toolDefinitions;
    }

    public String getProviderType() {
        return providerType;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getModelName() {
        return modelName;
    }

    public Map<String, Object> getExtraConfig() {
        return extraConfig;
    }

    public String getProviderId() {
        return providerId;
    }

    public List<ChatMessage> getMessages() {
        return messages;
    }

    public ChatOptions getOptions() {
        return options;
    }

    public Instant getDeadline() {
        return deadline;
    }

    public String getScenario() {
        return scenario;
    }

    public List<ToolDefinitionDTO> getToolDefinitions() {
        return toolDefinitions;
    }
}
