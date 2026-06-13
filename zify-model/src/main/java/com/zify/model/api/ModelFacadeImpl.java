package com.zify.model.api;

import com.zify.common.web.TextStreamSink;
import com.zify.model.api.dto.chat.ChatCompletionCommand;
import com.zify.model.api.dto.chat.ChatCompletionResult;
import com.zify.model.api.dto.model.ModelSummary;
import com.zify.model.domain.ModelService;
import com.zify.model.infrastructure.client.LlmChatGateway;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 模型模块 Facade 实现。
 * <p>
 * 模型 CRUD/连通性测试委托 {@link ModelService}；流式 Chat 网关委托 {@link LlmChatGateway}。
 */
@Service
public class ModelFacadeImpl implements ModelFacade {

    private final ModelService modelService;
    private final LlmChatGateway llmChatGateway;

    public ModelFacadeImpl(ModelService modelService, LlmChatGateway llmChatGateway) {
        this.modelService = modelService;
        this.llmChatGateway = llmChatGateway;
    }

    @Override
    public List<ModelSummary> listAvailableModels(String modelType) {
        return modelService.listAvailableModels(modelType);
    }

    @Override
    public ChatCompletionResult chatStream(ChatCompletionCommand command, TextStreamSink sink) {
        return llmChatGateway.chatStream(command, sink);
    }
}
