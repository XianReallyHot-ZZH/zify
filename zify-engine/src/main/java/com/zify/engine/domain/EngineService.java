package com.zify.engine.domain;

import com.zify.common.web.TextStreamSink;
import com.zify.engine.api.dto.ChatTurnCommand;
import com.zify.engine.api.dto.ChatTurnResult;
import org.springframework.stereotype.Service;

/**
 * 对话引擎 Service：纯编排入口，P2 起委托 {@link ReActLoop}（手动驱动多轮工具循环）。
 * <p>
 * 不读写任何数据库表。engine 全程不碰 DB；工具事件经 sink 推送；失败抛异常由 chat 发 run_error。
 */
@Service
public class EngineService {

    private final ReActLoop reActLoop;

    public EngineService(ReActLoop reActLoop) {
        this.reActLoop = reActLoop;
    }

    public ChatTurnResult runChatTurn(ChatTurnCommand cmd, TextStreamSink sink) {
        return reActLoop.run(cmd, sink);
    }
}
