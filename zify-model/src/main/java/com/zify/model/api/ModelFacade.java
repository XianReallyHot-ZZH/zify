package com.zify.model.api;

import com.zify.common.web.TextStreamSink;
import com.zify.model.api.dto.chat.ChatCompletionCommand;
import com.zify.model.api.dto.chat.ChatCompletionResult;
import com.zify.model.api.dto.model.ModelSummary;

import java.util.List;

/**
 * 模型模块 Facade 接口，供其他模块跨模块调用
 */
public interface ModelFacade {

    /**
     * 查询可用的模型列表（供 Agent / 工作流 / 知识库下拉框使用）
     * 条件：model.enabled=1 AND provider.status=ACTIVE AND 均未删除
     *
     * @param modelType 模型类型：LLM / EMBEDDING，null 则返回所有类型
     * @return 可用模型摘要列表
     */
    List<ModelSummary> listAvailableModels(String modelType);

    /**
     * 流式 Chat 调用：按 modelId 解析模型与供应商、解密 API Key、按 provider_type 选 Client，
     * 施加并发许可 / 超时 / 重试，token 经 sink 回调，返回最终结果。
     * <p>
     * API Key 仅在本模块内解密使用，不返回、不记录。失败抛
     * {@code com.zify.model.infrastructure.client.exception.LlmException}（engine/chat 决定如何处理）。
     *
     * @param command modelId + messages（含 system + 历史 + 本轮 user） + options（可空）
     * @param sink    流式增量回调
     * @return 累计全文 + finishReason + token 用量
     */
    ChatCompletionResult chatStream(ChatCompletionCommand command, TextStreamSink sink);
}
