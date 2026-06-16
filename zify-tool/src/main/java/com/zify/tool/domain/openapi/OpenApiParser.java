package com.zify.tool.domain.openapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zify.common.exception.BusinessException;
import com.zify.common.exception.ErrorCode;
import com.zify.tool.infrastructure.exception.ToolNonRetryableException;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * OpenAPI 3.0/3.1 解析器（Swagger Parser，glm-docs/13 §10.2 / P2 §六）。
 * <p>
 * parse → operation 预览（含 suggestedName 去重）；buildPlans → 复用 spec 文本重建每个选中 operation 的
 * HTTP 工具配置（inputSchema + paramsMapping + endpoint + method）。一个 operation → 一个 tool。
 */
@Component
public class OpenApiParser {

    private static final Logger log = LoggerFactory.getLogger(OpenApiParser.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_DESC = 512;
    private static final int MAX_FIELD_DESC = 200;

    private final OpenAPIV3Parser parser = new OpenAPIV3Parser();

    /**
     * 解析 spec → operation 预览列表（不持久化）。失败抛 {@link BusinessException}(OPENAPI_PARSE_FAILED)。
     */
    public OpenApiParseResult parse(String specContent) {
        OpenAPI open = parseOpenAPI(specContent);
        String baseUrl = firstBaseUrl(open);

        List<OperationPreview> previews = new ArrayList<>();
        Set<String> usedNames = new HashSet<>();
        if (open.getPaths() != null) {
            for (Map.Entry<String, PathItem> entry : open.getPaths().entrySet()) {
                String path = entry.getKey();
                PathItem item = entry.getValue();
                if (item == null) {
                    continue;
                }
                Map<PathItem.HttpMethod, Operation> ops = item.readOperationsMap();
                for (Map.Entry<PathItem.HttpMethod, Operation> op : ops.entrySet()) {
                    String method = op.getKey().name();
                    Operation operation = op.getValue();
                    OperationPreview preview = toPreview(operation, method, path);
                    preview.setSuggestedName(uniqueName(preview.getSuggestedName(), usedNames));
                    usedNames.add(preview.getSuggestedName());
                    previews.add(preview);
                }
            }
        }
        return new OpenApiParseResult(baseUrl, previews);
    }

    /**
     * 复用 spec 文本，为选中 operation 重建 HTTP 工具配置（glm-docs/13 §10.2）。
     *
     * @param specContent      原 spec 文本（导入请求回传）
     * @param baseUrlOverride  覆盖 spec server 的 baseUrl，可空
     * @param selections       每个操作的勾选/改名（按 operationId 或 method+path 匹配）
     * @param authSpec         统一鉴权（生成 headersTemplate 占位头），NONE 则不加
     */
    public List<ToolBuildPlan> buildPlans(String specContent, String baseUrlOverride,
                                          List<ImportSelection> selections, AuthSpec authSpec) {
        OpenAPI open = parseOpenAPI(specContent);
        String baseUrl = baseUrlOverride != null && !baseUrlOverride.isBlank()
                ? stripTrailingSlash(baseUrlOverride)
                : firstBaseUrl(open);
        if (baseUrl == null) {
            baseUrl = "";
        }
        Set<String> usedNames = new HashSet<>();
        List<ToolBuildPlan> plans = new ArrayList<>();
        if (open.getPaths() == null) {
            return plans;
        }
        for (Map.Entry<String, PathItem> entry : open.getPaths().entrySet()) {
            String path = entry.getKey();
            PathItem item = entry.getValue();
            if (item == null) {
                continue;
            }
            for (Map.Entry<PathItem.HttpMethod, Operation> op : item.readOperationsMap().entrySet()) {
                String method = op.getKey().name();
                Operation operation = op.getValue();
                ImportSelection sel = matchSelection(selections, operation, method, path);
                if (sel == null || !sel.isSelected()) {
                    continue;
                }
                String name = sel.getName() != null && !sel.getName().isBlank()
                        ? sel.getName()
                        : suggestedName(operation, method, path);
                name = uniqueName(sanitize(name), usedNames);
                usedNames.add(name);
                plans.add(toToolConfig(open, operation, method, path, baseUrl, name, authSpec));
            }
        }
        return plans;
    }

    /** 单个 operation → HTTP 工具配置（inputSchema + configJson + endpoint + method）。 */
    private ToolBuildPlan toToolConfig(OpenAPI open, Operation operation, String method, String path,
                                       String baseUrl, String name, AuthSpec authSpec) {
        ToolBuildPlan plan = new ToolBuildPlan();
        plan.setName(name);
        plan.setMethod(method.toUpperCase());
        plan.setEndpoint(baseUrl + path);
        plan.setDescription(truncate(pickDescription(operation), MAX_DESC));

        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        List<Map<String, Object>> paramsMapping = new ArrayList<>();

        // 1) path/query/header 参数
        if (operation.getParameters() != null) {
            for (Parameter param : operation.getParameters()) {
                if (param == null || param.getName() == null) {
                    continue;
                }
                String pName = param.getName();
                String in = param.getIn() == null ? "query" : param.getIn(); // path/query/header/cookie
                if ("cookie".equals(in)) {
                    continue;
                }
                properties.put(pName, propertySchema(resolveSchemaRef(open, param.getSchema()), param.getDescription()));
                if (Boolean.TRUE.equals(param.getRequired())) {
                    required.add(pName);
                }
                paramsMapping.add(mappingEntry(pName, in, Boolean.TRUE.equals(param.getRequired())));
            }
        }

        // 2) requestBody：对象 schema 的字段内联为 body 参数
        RequestBody body = operation.getRequestBody();
        if (body != null) {
            Schema<?> bodySchema = resolveSchemaRef(open, extractBodySchema(body));
            if (bodySchema != null && bodySchema.getProperties() != null) {
                for (Map.Entry<String, Schema> f : bodySchema.getProperties().entrySet()) {
                    String fName = f.getKey();
                    properties.put(fName, propertySchema(f.getValue(), null));
                    paramsMapping.add(mappingEntry(fName, "body", false));
                }
            } else {
                // 非 object body：放一个 body 占位
                properties.put("body", Map.of("type", "string", "description", "请求体"));
                paramsMapping.add(mappingEntry("body", "body", false));
            }
        }

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }
        plan.setInputSchema(toJson(schema));

        // 3) config_json：paramsMapping + headersTemplate（含鉴权占位头）+ bodyTemplate(null，由 HttpTool 序列化 body 字段)
        List<Map<String, Object>> headersTemplate = new ArrayList<>();
        headersTemplate.add(headerEntry("Content-Type", "application/json", false));
        appendAuthHeader(headersTemplate, authSpec);
        Map<String, Object> configJson = new LinkedHashMap<>();
        configJson.put("paramsMapping", paramsMapping);
        configJson.put("headersTemplate", headersTemplate);
        configJson.put("bodyTemplate", null);
        plan.setConfigJson(configJson);
        return plan;
    }

    // ── 解析 ───────────────────────────────────────────────

    private OpenAPI parseOpenAPI(String specContent) {
        if (specContent == null || specContent.isBlank()) {
            throw new BusinessException(ErrorCode.OPENAPI_PARSE_FAILED);
        }
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        options.setFlatten(true);
        try {
            SwaggerParseResult result = parser.readContents(specContent, null, options);
            if (result == null || result.getOpenAPI() == null) {
                log.warn("OpenAPI parse returned null: messages={}", result == null ? "[]" : result.getMessages());
                throw new BusinessException(ErrorCode.OPENAPI_PARSE_FAILED);
            }
            return result.getOpenAPI();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("OpenAPI parse failed: {}", e.getMessage());
            throw new BusinessException(ErrorCode.OPENAPI_PARSE_FAILED);
        }
    }

    private OperationPreview toPreview(Operation operation, String method, String path) {
        OperationPreview preview = new OperationPreview();
        preview.setOperationId(operation.getOperationId());
        preview.setMethod(method.toUpperCase());
        preview.setPath(path);
        preview.setSummary(pickDescription(operation));
        preview.setSuggestedName(suggestedName(operation, method, path));
        preview.setHasRequestBody(operation.getRequestBody() != null);
        return preview;
    }

    private String suggestedName(Operation operation, String method, String path) {
        String opId = operation.getOperationId();
        if (opId != null && !opId.isBlank()) {
            return sanitize(opId);
        }
        return sanitize(method + "_" + path);
    }

    private String pickDescription(Operation operation) {
        String s = operation.getSummary();
        if (s == null || s.isBlank()) {
            s = operation.getDescription();
        }
        return s == null ? "" : s;
    }

    private ImportSelection matchSelection(List<ImportSelection> selections, Operation operation,
                                           String method, String path) {
        if (selections == null) {
            return null;
        }
        String opId = operation.getOperationId();
        for (ImportSelection sel : selections) {
            if (opId != null && !opId.isBlank() && opId.equals(sel.getOperationId())) {
                return sel;
            }
            if (method.equalsIgnoreCase(sel.getMethod()) && path.equals(sel.getPath())) {
                return sel;
            }
        }
        return null;
    }

    /** 跟随 $ref 解析 components/schemas（最多 5 层），把引用 schema 换成实际 schema。 */
    private Schema<?> resolveSchemaRef(OpenAPI open, Schema<?> schema) {
        if (schema == null || open == null || open.getComponents() == null || open.getComponents().getSchemas() == null) {
            return schema;
        }
        int guard = 0;
        while (schema.get$ref() != null && guard < 5) {
            String ref = schema.get$ref();
            String name = ref.substring(ref.lastIndexOf('/') + 1);
            Schema<?> resolved = open.getComponents().getSchemas().get(name);
            if (resolved == null) {
                break;
            }
            schema = resolved;
            guard++;
        }
        return schema;
    }

    private Schema<?> extractBodySchema(RequestBody body) {
        if (body.get$ref() != null || body.getContent() == null) {
            return null;
        }
        MediaType json = body.getContent().get("application/json");
        if (json == null) {
            // 退而取首个 mediaType
            json = body.getContent().values().stream().findFirst().orElse(null);
        }
        return json == null ? null : json.getSchema();
    }

    private Map<String, Object> propertySchema(Schema<?> schema, String description) {
        Map<String, Object> prop = new LinkedHashMap<>();
        prop.put("type", jsonSchemaType(schema));
        String desc = description != null ? description
                : (schema != null ? schema.getDescription() : null);
        if (desc != null && !desc.isBlank()) {
            prop.put("description", truncate(desc, MAX_FIELD_DESC));
        }
        return prop;
    }

    private String jsonSchemaType(Schema<?> schema) {
        if (schema == null) {
            return "string";
        }
        String t = schema.getType();
        if (t == null || t.isBlank()) {
            return schema.getProperties() != null ? "object" : "string";
        }
        return switch (t) {
            case "integer", "number", "boolean", "string", "array", "object" -> t;
            default -> "string";
        };
    }

    private Map<String, Object> mappingEntry(String name, String in, boolean required) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("in", in);
        m.put("required", required);
        return m;
    }

    private Map<String, Object> headerEntry(String name, String value, boolean secret) {
        Map<String, Object> h = new LinkedHashMap<>();
        h.put("name", name);
        h.put("value", value);
        h.put("secret", secret);
        return h;
    }

    private void appendAuthHeader(List<Map<String, Object>> headersTemplate, AuthSpec authSpec) {
        if (authSpec == null || authSpec.getType() == null || "NONE".equals(authSpec.getType())) {
            return;
        }
        switch (authSpec.getType()) {
            case "API_KEY" -> headersTemplate.add(headerEntry(
                    authSpec.getHeaderName() == null ? "X-Api-Key" : authSpec.getHeaderName(),
                    "{{auth.apiKey}}", true));
            case "BEARER" -> headersTemplate.add(headerEntry("Authorization", "Bearer {{auth.token}}", true));
            default -> { /* ignore */ }
        }
    }

    private String firstBaseUrl(OpenAPI open) {
        if (open.getServers() == null || open.getServers().isEmpty()) {
            return null;
        }
        Server server = open.getServers().get(0);
        return server == null || server.getUrl() == null ? null : stripTrailingSlash(server.getUrl());
    }

    private String stripTrailingSlash(String url) {
        return url != null && url.endsWith("/") && url.length() > 1 ? url.substring(0, url.length() - 1) : url;
    }

    private String uniqueName(String candidate, Set<String> used) {
        String base = sanitize(candidate);
        if (base.isEmpty()) {
            base = "tool";
        }
        String name = base;
        int i = 2;
        while (used.contains(name)) {
            name = base + "_" + i;
            i++;
        }
        return name;
    }

    /** 转小写蛇形：camelCase 边界加下划线，非 [a-z0-9] 合并为下划线。 */
    private String sanitize(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
        s = s.replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        return s;
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() > max ? s.substring(0, max) : s;
    }

    private String toJson(Object o) {
        try {
            return MAPPER.writeValueAsString(o);
        } catch (Exception e) {
            throw new ToolNonRetryableException("failed to serialize input schema",
                    null, null, "openapi_import", e);
        }
    }
}
