package com.zify.tool.domain.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zify.common.persistence.id.IdGenerator;
import com.zify.tool.api.dto.ToolExecContext;
import com.zify.tool.domain.Tool;
import com.zify.tool.domain.ToolExecutionResult;
import com.zify.tool.domain.ToolView;
import com.zify.tool.infrastructure.client.ToolExecSupport;
import com.zify.tool.infrastructure.client.http.HttpClientFactory;
import com.zify.tool.infrastructure.entity.ToolCallLogEntity;
import com.zify.tool.infrastructure.entity.ToolEntity;
import com.zify.tool.infrastructure.exception.ToolCircuitOpenException;
import com.zify.tool.infrastructure.exception.ToolCancelledException;
import com.zify.tool.infrastructure.exception.ToolException;
import com.zify.tool.infrastructure.exception.ToolNonRetryableException;
import com.zify.tool.infrastructure.exception.ToolRetryableException;
import com.zify.tool.infrastructure.exception.ToolTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTTP 工具实现（glm-docs/13 §十 / P2 §五）。
 * <p>
 * 按 {@code config_json.paramsMapping} 把 args 映射到 path/query/header/body，注入鉴权凭据
 * （从 auth_config 解密，明文仅 execute 栈内），SSRF 运行时校验 + 请求体大小校验 + 重试（幂等驱动）
 * + 熔断（per tool_id），响应截断，写 tool_call_log（执行点即记录点）。
 * 失败返回 {@code status=ERROR}（不抛，回灌模型）；仅 {@link ToolCancelledException} 致命上抛。
 * <p>
 * 无可变状态：按执行点构造，快照只读，并行安全（13 §3.3）。
 */
public class HttpTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(HttpTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(args|auth)\\.([A-Za-z0-9_.\\-]+)\\}\\}");

    private static final String SCENARIO = "execute";

    private final ToolEntity snapshot;
    private final ToolExecSupport support;
    private final HttpClientFactory httpClientFactory;

    public HttpTool(ToolEntity snapshot, ToolExecSupport support, HttpClientFactory httpClientFactory) {
        this.snapshot = snapshot;
        this.support = support;
        this.httpClientFactory = httpClientFactory;
    }

    @Override
    public ToolView toView() {
        return new ToolView(snapshot.getId(), snapshot.getName(), snapshot.getDescription(),
                snapshot.getInputSchema(), snapshot.getSourceType());
    }

    @Override
    @SuppressWarnings("unchecked")
    public ToolExecutionResult execute(Map<String, Object> args, ToolExecContext ctx) {
        Map<String, Object> safeArgs = args == null ? Collections.emptyMap() : args;
        long start = System.nanoTime();
        String toolId = snapshot.getId();
        String toolName = snapshot.getName();
        boolean idempotent = snapshot.getIdempotent() != null && snapshot.getIdempotent() == 1;

        try {
            Map<String, Object> configJson = snapshot.getConfigJson();
            Map<String, Object> authMap = decryptAuth();

            RequestSpec spec = buildRequest(safeArgs, authMap, configJson);
            support.getSsrfGuard().validate(spec.url, toolId, toolName, SCENARIO);
            support.getResponseSizer().checkRequestSize(spec.bodyBytes());

            Duration budget = requestBudget();
            Instant deadline = Instant.now().plus(budget);

            String responseBody = support.getCircuitBreaker().execute(toolId, toolName, SCENARIO,
                    () -> support.getRetryWrapper().withRetry(idempotent, deadline,
                            () -> doRequest(spec, budget), toolId, toolName, SCENARIO));

            long durationMs = durationMs(start);
            String output = support.getResponseSizer().truncate(responseBody);
            String logId = writeLog(ctx, safeArgs, output, "SUCCESS", durationMs, null);
            log.info("event=tool_call scenario={} source=HTTP tool={} status=success durationMs={}",
                    SCENARIO, toolName, durationMs);
            return new ToolExecutionResult("SUCCESS", output, durationMs, logId, null);
        } catch (ToolCancelledException e) {
            // 致命：进行中取消不写 log，上抛 engine（中断循环）
            log.info("event=tool_call scenario={} source=HTTP tool={} status=cancelled", SCENARIO, toolName);
            throw e;
        } catch (ToolException e) {
            long durationMs = durationMs(start);
            String status = mapStatus(e);
            String err = friendlyError(e);
            String logId = writeLog(ctx, safeArgs, null, status, durationMs, err);
            String fallback = fallbackText(toolName, e);
            log.warn("event=tool_call scenario={} source=HTTP tool={} status={} durationMs={} error={}",
                    SCENARIO, toolName, status, durationMs, err);
            return new ToolExecutionResult("ERROR", fallback, durationMs, logId, err);
        }
    }

    // ── 请求构造 ───────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private RequestSpec buildRequest(Map<String, Object> args, Map<String, Object> authMap,
                                     Map<String, Object> configJson) {
        String method = snapshot.getMethod() == null ? "GET" : snapshot.getMethod().toUpperCase();
        String endpoint = snapshot.getEndpoint() == null ? "" : snapshot.getEndpoint();

        List<Map<String, Object>> paramsMapping = listField(configJson, "paramsMapping");
        List<Map<String, Object>> headersTemplate = listField(configJson, "headersTemplate");
        String bodyTemplate = configJson == null ? null : (String) configJson.get("bodyTemplate");

        // 1) args 按 paramsMapping 映射
        List<String[]> queryParams = new ArrayList<>();
        List<String[]> headerParams = new ArrayList<>();
        Map<String, Object> bodyFields = new LinkedHashMap<>();
        String filledEndpoint = endpoint;
        for (Map<String, Object> pm : paramsMapping) {
            String name = str(pm.get("name"));
            String in = str(pm.get("in"));
            if (!args.containsKey(name)) {
                continue;
            }
            String val = str(args.get(name));
            switch (in) {
                case "path" -> filledEndpoint = filledEndpoint.replace("{" + name + "}", encode(val));
                case "query" -> queryParams.add(new String[]{name, val});
                case "header" -> headerParams.add(new String[]{name, val});
                case "body" -> bodyFields.put(name, args.get(name));
                default -> { /* ignore unknown in */ }
            }
        }

        // 2) headersTemplate（{{auth.x}} / {{args.x}} 占位替换）
        List<String[]> headers = new ArrayList<>(headerParams);
        for (Map<String, Object> h : headersTemplate) {
            String name = str(h.get("name"));
            String value = replacePlaceholders(str(h.get("value")), args, authMap);
            headers.add(new String[]{name, value});
        }

        // 3) 鉴权注入（auth_type 存在 auth_config.type 内）
        injectAuth(headers, authMap);

        // 4) body
        String body = null;
        if (bodyTemplate != null && !bodyTemplate.isBlank()) {
            body = replacePlaceholders(bodyTemplate, args, authMap);
        } else if (!bodyFields.isEmpty()) {
            body = toJson(bodyFields);
        }

        String url = appendQuery(filledEndpoint, queryParams);
        return new RequestSpec(method, url, headers, body);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> decryptAuth() {
        String cipher = snapshot.getAuthConfig();
        if (cipher == null || cipher.isBlank()) {
            return null;
        }
        try {
            String plain = support.getSecretEncryptor().decrypt(cipher);
            if (plain == null || plain.isBlank()) {
                return null;
            }
            return MAPPER.readValue(plain, Map.class);
        } catch (Exception e) {
            throw new ToolNonRetryableException("failed to decrypt auth config",
                    snapshot.getId(), snapshot.getName(), SCENARIO, e);
        }
    }

    private void injectAuth(List<String[]> headers, Map<String, Object> authMap) {
        if (authMap == null) {
            return;
        }
        String type = str(authMap.get("type"));
        switch (type) {
            case "API_KEY" -> headers.add(new String[]{str(authMap.get("headerName")), str(authMap.get("apiKey"))});
            case "BEARER" -> headers.add(new String[]{"Authorization", "Bearer " + str(authMap.get("token"))});
            default -> { /* NONE or unknown：仅依赖模板占位 */ }
        }
    }

    private String replacePlaceholders(String template, Map<String, Object> args, Map<String, Object> authMap) {
        if (template == null) {
            return null;
        }
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String scope = m.group(1);
            String key = m.group(2);
            Object val = "auth".equals(scope) && authMap != null ? authMap.get(key) : args.get(key);
            m.appendReplacement(sb, Matcher.quoteReplacement(val == null ? "" : str(val)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    // ── 执行 ───────────────────────────────────────────────

    private String doRequest(RequestSpec spec, Duration budget) {
        RestClient client = httpClientFactory.build(support.getProperties().getTimeout().getConnect(), budget);
        RestClient.RequestBodySpec request = client.method(HttpMethod.valueOf(spec.method)).uri(spec.url);
        boolean hasContentType = false;
        for (String[] h : spec.headers) {
            if ("Content-Type".equalsIgnoreCase(h[0])) {
                hasContentType = true;
            }
            request.header(h[0], h[1]);
        }
        try {
            if (spec.body != null) {
                if (!hasContentType) {
                    request.contentType(MediaType.APPLICATION_JSON);
                }
                return request.body(spec.body).retrieve().body(String.class);
            }
            return request.retrieve().body(String.class);
        } catch (ResourceAccessException e) {
            throw new ToolRetryableException("http io error: " + brief(e),
                    snapshot.getId(), snapshot.getName(), SCENARIO, classifySent(e));
        } catch (HttpClientErrorException e) {
            // 4xx：不可重试
            throw new ToolNonRetryableException("http " + e.getStatusCode().value(),
                    snapshot.getId(), snapshot.getName(), SCENARIO, e);
        } catch (HttpServerErrorException e) {
            // 5xx：请求已发出，仅幂等可重试
            throw new ToolRetryableException("http " + e.getStatusCode().value(),
                    snapshot.getId(), snapshot.getName(), SCENARIO, true);
        } catch (RestClientException e) {
            throw new ToolRetryableException("http client error: " + brief(e),
                    snapshot.getId(), snapshot.getName(), SCENARIO, false);
        }
    }

    /** 推断请求是否已送达对端（决定请求发出后失败的重试安全性，13 §5.2）。 */
    private boolean classifySent(Throwable t) {
        Throwable c = t;
        while (c != null) {
            String name = c.getClass().getName();
            String msg = c.getMessage() == null ? "" : c.getMessage().toLowerCase();
            if (c instanceof java.net.ConnectException) {
                return false;
            }
            if (name.contains("ConnectTimeout") || (msg.contains("connect") && msg.contains("timeout"))) {
                return false;
            }
            if (c instanceof java.net.SocketTimeoutException) {
                return true; // 读超时
            }
            if (name.contains("HttpTimeoutException")) {
                return msg.contains("read") || !msg.contains("connect");
            }
            c = c.getCause();
        }
        return false;
    }

    // ── 日志 ───────────────────────────────────────────────

    private String writeLog(ToolExecContext ctx, Map<String, Object> args, String output,
                            String status, long durationMs, String error) {
        ToolCallLogEntity entity = new ToolCallLogEntity();
        entity.setId(IdGenerator.uuid());
        entity.setToolId(snapshot.getId());
        entity.setToolName(snapshot.getName());
        entity.setSourceType(snapshot.getSourceType());
        entity.setMcpServerId(snapshot.getMcpServerId());
        if (ctx != null) {
            entity.setAgentId(ctx.getAgentId());
            entity.setConversationId(ctx.getConversationId());
            entity.setTurn(ctx.getTurn());
            entity.setToolCallId(ctx.getToolCallId());
        }
        entity.setInput(truncateInput(args));
        entity.setOutput(output);
        entity.setStatus(status);
        entity.setDurationMs((int) Math.min(durationMs, Integer.MAX_VALUE));
        entity.setError(error);
        support.getToolCallLogMapper().insert(entity);
        return entity.getId();
    }

    private Map<String, Object> truncateInput(Map<String, Object> args) {
        if (args == null || args.isEmpty()) {
            return null;
        }
        try {
            String json = toJson(args);
            int cap = support.getProperties().getSecurity().getResponseMaxBytes();
            if (json.getBytes(StandardCharsets.UTF_8).length <= cap) {
                return args;
            }
            String truncated = support.getResponseSizer().truncate(json, cap);
            try {
                return MAPPER.readValue(truncated, Map.class);
            } catch (IOException invalid) {
                Map<String, Object> wrap = new LinkedHashMap<>();
                wrap.put("_input_truncated", truncated);
                return wrap;
            }
        } catch (Exception e) {
            return null;
        }
    }

    // ── 失败回灌（13 §6.2） ─────────────────────────────────

    private String fallbackText(String toolName, ToolException e) {
        if (e instanceof ToolCircuitOpenException) {
            return "工具 " + toolName + " 当前不可用";
        }
        if (e instanceof ToolTimeoutException || e instanceof ToolRetryableException) {
            return "工具 " + toolName + " 暂时不可用，请稍后重试或换一种方式";
        }
        if (e instanceof ToolNonRetryableException) {
            return "调用工具 " + toolName + " 失败：" + friendlyError(e);
        }
        return "工具 " + toolName + " 执行失败：" + friendlyError(e);
    }

    private String mapStatus(ToolException e) {
        if (e instanceof ToolTimeoutException) {
            return "TIMEOUT";
        }
        if (e instanceof ToolCircuitOpenException) {
            return "CIRCUIT_OPEN";
        }
        return "ERROR";
    }

    private String friendlyError(ToolException e) {
        String msg = e.getMessage();
        if (msg == null || msg.isBlank()) {
            return "unknown error";
        }
        return msg.length() > 200 ? msg.substring(0, 200) : msg;
    }

    // ── 工具方法 ───────────────────────────────────────────

    private Duration requestBudget() {
        Duration requestTimeout = snapshot.getTimeoutSeconds() != null
                ? Duration.ofSeconds(snapshot.getTimeoutSeconds())
                : support.getProperties().getTimeout().getRequestDefault();
        Duration totalCap = support.getProperties().getTimeout().getTotalCap();
        return requestTimeout.compareTo(totalCap) <= 0 ? requestTimeout : totalCap;
    }

    private long durationMs(long startNanos) {
        return Math.max(0, (System.nanoTime() - startNanos) / 1_000_000L);
    }

    private String appendQuery(String endpoint, List<String[]> queryParams) {
        if (queryParams.isEmpty()) {
            return endpoint;
        }
        StringBuilder sb = new StringBuilder(endpoint);
        sb.append(endpoint.contains("?") ? "&" : "?");
        for (int i = 0; i < queryParams.size(); i++) {
            if (i > 0) {
                sb.append("&");
            }
            sb.append(encode(queryParams.get(i)[0])).append("=").append(encode(queryParams.get(i)[1]));
        }
        return sb.toString();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private String toJson(Object o) {
        try {
            return MAPPER.writeValueAsString(o);
        } catch (Exception e) {
            return String.valueOf(o);
        }
    }

    private static String brief(Throwable e) {
        String msg = e.getMessage();
        return msg == null ? e.getClass().getSimpleName() : (msg.length() > 120 ? msg.substring(0, 120) : msg);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> listField(Map<String, Object> configJson, String key) {
        if (configJson == null) {
            return List.of();
        }
        Object v = configJson.get(key);
        return v instanceof List ? (List<Map<String, Object>>) v : List.of();
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    /** 内部请求规格。 */
    private static final class RequestSpec {
        final String method;
        final String url;
        final List<String[]> headers;
        final String body;

        RequestSpec(String method, String url, List<String[]> headers, String body) {
            this.method = method;
            this.url = url;
            this.headers = headers;
            this.body = body;
        }

        int bodyBytes() {
            return body == null ? 0 : body.getBytes(StandardCharsets.UTF_8).length;
        }
    }
}
