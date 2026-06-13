package com.zify.engine.api;

import com.zify.common.web.TextStreamSink;
import com.zify.engine.api.dto.ChatTurnCommand;
import com.zify.engine.api.dto.ChatTurnResult;

/**
 * 引擎模块 Facade 接口，供 chat 跨模块调用（chat → engine 编排方向）。
 * engine 是纯编排 Facade，不读写任何数据库表。
 */
public interface EngineFacade {

    /**
     * 执行单轮对话：取 Agent 配置 → 组装 Prompt → 调 LLM 流式生成，token 经 sink 回调。
     * 失败抛 {@code LlmException}，由 chat 决定如何发 run_error。
     */
    ChatTurnResult runChatTurn(ChatTurnCommand command, TextStreamSink sink);
}
