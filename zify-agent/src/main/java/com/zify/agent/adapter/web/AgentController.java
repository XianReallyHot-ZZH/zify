package com.zify.agent.adapter.web;

import com.zify.agent.api.dto.AgentListQuery;
import com.zify.agent.api.dto.AgentResponse;
import com.zify.agent.api.dto.CreateAgentRequest;
import com.zify.agent.api.dto.UpdateAgentRequest;
import com.zify.agent.api.dto.UpdateAgentStatusRequest;
import com.zify.agent.domain.AgentService;
import com.zify.common.web.PageResult;
import com.zify.common.web.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent 管理 Controller。无业务逻辑，仅调用本模块 {@link AgentService}。
 */
@RestController
@RequestMapping("/api/agents")
public class AgentController {

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    @PostMapping
    public Result<AgentResponse> create(@Valid @RequestBody CreateAgentRequest request) {
        return Result.ok(agentService.createAgent(request));
    }

    @GetMapping
    public Result<PageResult<AgentResponse>> list(@ModelAttribute AgentListQuery query) {
        return Result.ok(agentService.listAgents(query));
    }

    @GetMapping("/{id}")
    public Result<AgentResponse> get(@PathVariable String id) {
        return Result.ok(agentService.getAgent(id));
    }

    @PutMapping("/{id}")
    public Result<AgentResponse> update(@PathVariable String id,
                                        @Valid @RequestBody UpdateAgentRequest request) {
        return Result.ok(agentService.updateAgent(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        agentService.deleteAgent(id);
        return Result.ok();
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable String id,
                                     @Valid @RequestBody UpdateAgentStatusRequest request) {
        agentService.updateStatus(id, request.getStatus());
        return Result.ok();
    }
}
