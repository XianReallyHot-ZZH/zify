package com.zify.model.api;

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
}
