package com.zify.model.api.dto.chat;

import java.util.List;

/**
 * 流式 Chat 调用入参。
 * <p>
 * messages 含 system + 历史 + 本轮 user；options 可空，空字段回退 model.default_params。
 */
public class ChatCompletionCommand {

    /** 目标模型 ID（model.id） */
    private String modelId;

    /** 对话消息序列（system + 历史 + 本轮 user） */
    private List<ChatMessage> messages;

    /** 调用参数，可空 */
    private ChatOptions options;

    public ChatCompletionCommand() {
    }

    public ChatCompletionCommand(String modelId, List<ChatMessage> messages, ChatOptions options) {
        this.modelId = modelId;
        this.messages = messages;
        this.options = options;
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public List<ChatMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<ChatMessage> messages) {
        this.messages = messages;
    }

    public ChatOptions getOptions() {
        return options;
    }

    public void setOptions(ChatOptions options) {
        this.options = options;
    }
}
