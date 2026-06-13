package com.zify.engine.infrastructure.facade;

import com.zify.common.web.TextStreamSink;
import com.zify.engine.api.EngineFacade;
import com.zify.engine.api.dto.ChatTurnCommand;
import com.zify.engine.api.dto.ChatTurnResult;
import com.zify.engine.domain.EngineService;
import org.springframework.stereotype.Service;

/**
 * 引擎 Facade 实现，转发到 {@link EngineService}。
 */
@Service
public class EngineFacadeImpl implements EngineFacade {

    private final EngineService engineService;

    public EngineFacadeImpl(EngineService engineService) {
        this.engineService = engineService;
    }

    @Override
    public ChatTurnResult runChatTurn(ChatTurnCommand command, TextStreamSink sink) {
        return engineService.runChatTurn(command, sink);
    }
}
