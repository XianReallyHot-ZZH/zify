package com.zify.model.domain.handler;

import com.zify.model.api.dto.model.ModelTestResult;
import com.zify.model.api.dto.provider.ProviderTestResult;
import com.zify.model.domain.ProviderType;

import java.util.Map;
import java.util.Set;

/**
 * 供应商连通性测试策略接口
 * <p>
 * 每种供应商类型实现此接口，由 Spring 自动注册到 ModelService 的 handler 路由表。
 * 新增供应商只需：① 在 ProviderType 加枚举值 ② 新建 Handler 加 @Component。
 */
public interface ProviderTestHandler {

    /**
     * 声明本 Handler 支持的供应商类型
     * <p>
     * OPENAI 和 OPENAI_COMPATIBLE 可返回多个值以共用同一个 Handler。
     */
    Set<ProviderType> supportedTypes();

    /**
     * 测试供应商连接
     *
     * @param baseUrl    供应商 API 地址
     * @param apiKey     解密后的 API Key，可能为 null
     * @param extraConfig 供应商额外配置（如 Anthropic 的 apiVersion）
     * @param startMs    测试开始时间戳（System.currentTimeMillis()），用于计算延迟
     */
    ProviderTestResult testConnection(String baseUrl, String apiKey,
                                      Map<String, Object> extraConfig, long startMs);

    /**
     * 测试 LLM 模型可用性
     *
     * @param baseUrl    供应商 API 地址
     * @param apiKey     解密后的 API Key，可能为 null
     * @param modelName  模型名称
     * @param extraConfig 供应商额外配置
     * @param startMs    测试开始时间戳
     */
    ModelTestResult testLlmModel(String baseUrl, String apiKey, String modelName,
                                 Map<String, Object> extraConfig, long startMs);

    /**
     * 测试 Embedding 模型可用性
     *
     * @param baseUrl    供应商 API 地址
     * @param apiKey     解密后的 API Key，可能为 null
     * @param modelName  模型名称
     * @param startMs    测试开始时间戳
     */
    ModelTestResult testEmbeddingModel(String baseUrl, String apiKey, String modelName, long startMs);
}
