package com.zify.tool.adapter.web;

import com.zify.common.web.PageResult;
import com.zify.common.web.Result;
import com.zify.tool.api.dto.CreateToolRequest;
import com.zify.tool.api.dto.ImportOpenApiRequest;
import com.zify.tool.api.dto.OpenApiParseResponse;
import com.zify.tool.api.dto.ParseOpenApiRequest;
import com.zify.tool.api.dto.ToolDetailResponse;
import com.zify.tool.api.dto.ToolImportResult;
import com.zify.tool.api.dto.ToolListQuery;
import com.zify.tool.api.dto.ToolSummaryResponse;
import com.zify.tool.api.dto.ToolTestRequest;
import com.zify.tool.api.dto.ToolTestResult;
import com.zify.tool.api.dto.UpdateEnabledRequest;
import com.zify.tool.api.dto.UpdateToolRequest;
import com.zify.tool.domain.ToolService;
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
 * 工具管理 Controller（glm-docs/13 §十 / P2 §12.2）。无业务逻辑，仅调本模块 Service。
 */
@RestController
@RequestMapping("/api/tool/tools")
public class ToolController {

    private final ToolService service;

    public ToolController(ToolService service) {
        this.service = service;
    }

    @PostMapping
    public Result<ToolDetailResponse> create(@RequestBody CreateToolRequest request) {
        return Result.ok(service.create(request));
    }

    @PostMapping("/parse-openapi")
    public Result<OpenApiParseResponse> parseOpenApi(@RequestBody ParseOpenApiRequest request) {
        return Result.ok(service.parseOpenApi(request.getSpec()));
    }

    @PostMapping("/import-openapi")
    public Result<ToolImportResult> importOpenApi(@RequestBody ImportOpenApiRequest request) {
        return Result.ok(service.importOpenApi(request));
    }

    @GetMapping
    public Result<PageResult<ToolSummaryResponse>> list(@ModelAttribute ToolListQuery query) {
        return Result.ok(service.list(query));
    }

    @GetMapping("/{id}")
    public Result<ToolDetailResponse> get(@PathVariable String id) {
        return Result.ok(service.get(id));
    }

    @PutMapping("/{id}")
    public Result<ToolDetailResponse> update(@PathVariable String id, @RequestBody UpdateToolRequest request) {
        return Result.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        service.delete(id);
        return Result.ok();
    }

    @PutMapping("/{id}/enabled")
    public Result<ToolDetailResponse> setEnabled(@PathVariable String id,
                                                 @RequestBody UpdateEnabledRequest request) {
        boolean enabled = request.getEnabled() == null || request.getEnabled();
        return Result.ok(service.setEnabled(id, enabled));
    }

    @PostMapping("/{id}/test")
    public Result<ToolTestResult> test(@PathVariable String id, @RequestBody ToolTestRequest request) {
        return Result.ok(service.test(id, request.getArgs()));
    }
}
