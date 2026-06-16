package com.zify.tool.adapter.web;

import com.zify.common.web.PageResult;
import com.zify.common.web.Result;
import com.zify.tool.api.dto.CreateMcpServerRequest;
import com.zify.tool.api.dto.McpServerDetailResponse;
import com.zify.tool.api.dto.McpServerListQuery;
import com.zify.tool.api.dto.McpServerSummaryResponse;
import com.zify.tool.api.dto.McpServerTestResult;
import com.zify.tool.api.dto.UpdateEnabledRequest;
import com.zify.tool.api.dto.UpdateMcpServerRequest;
import com.zify.tool.domain.McpServerService;
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
 * MCP Server 管理 Controller（glm-docs/13 §9 / P2 §12.1）。无业务逻辑，仅调本模块 Service。
 */
@RestController
@RequestMapping("/api/tool/mcp-servers")
public class McpServerController {

    private final McpServerService service;

    public McpServerController(McpServerService service) {
        this.service = service;
    }

    @PostMapping
    public Result<McpServerDetailResponse> create(@RequestBody CreateMcpServerRequest request) {
        return Result.ok(service.create(request));
    }

    @GetMapping
    public Result<PageResult<McpServerSummaryResponse>> list(@ModelAttribute McpServerListQuery query) {
        return Result.ok(service.list(query));
    }

    @GetMapping("/{id}")
    public Result<McpServerDetailResponse> get(@PathVariable String id) {
        return Result.ok(service.get(id));
    }

    @PutMapping("/{id}")
    public Result<McpServerDetailResponse> update(@PathVariable String id,
                                                  @RequestBody UpdateMcpServerRequest request) {
        return Result.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        service.delete(id);
        return Result.ok();
    }

    @PutMapping("/{id}/enabled")
    public Result<McpServerDetailResponse> setEnabled(@PathVariable String id,
                                                      @RequestBody UpdateEnabledRequest request) {
        boolean enabled = request.getEnabled() == null || request.getEnabled();
        return Result.ok(service.setEnabled(id, enabled));
    }

    @PostMapping("/{id}/test")
    public Result<McpServerTestResult> test(@PathVariable String id) {
        return Result.ok(service.test(id));
    }

    @PostMapping("/{id}/refresh")
    public Result<McpServerDetailResponse> refresh(@PathVariable String id) {
        return Result.ok(service.refresh(id));
    }

    @PostMapping("/test")
    public Result<McpServerTestResult> testConfig(@RequestBody CreateMcpServerRequest request) {
        return Result.ok(service.testConfig(request));
    }
}
