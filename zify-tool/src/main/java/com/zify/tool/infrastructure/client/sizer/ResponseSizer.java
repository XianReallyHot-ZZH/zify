package com.zify.tool.infrastructure.client.sizer;

import com.zify.tool.config.ToolProperties;
import com.zify.tool.infrastructure.exception.ToolNonRetryableException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * 响应截断 + 请求体大小校验（glm-docs/13 §8.2）。
 * <p>
 * truncate 按 UTF-8 字节计，超阈值截断并追加 {@code ...[truncated]}，且不截断多字节字符中段。
 * 回灌模型 / 存 message / 存 tool_call_log 三处用同一份截断内容。
 */
@Component
public class ResponseSizer {

    private static final String TRUNCATED_SUFFIX = "...[truncated]";

    private final ToolProperties properties;

    public ResponseSizer(ToolProperties properties) {
        this.properties = properties;
    }

    /** 用配置的 response-max-bytes 截断。 */
    public String truncate(String text) {
        return truncate(text, properties.getSecurity().getResponseMaxBytes());
    }

    /**
     * 按 maxBytes（UTF-8 字节计）截断。超阈值则截断到字符边界 + 追加截断标记。
     */
    public String truncate(String text, int maxBytes) {
        if (text == null) {
            return null;
        }
        if (maxBytes <= 0) {
            return "";
        }
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= maxBytes) {
            return text;
        }
        byte[] suffix = TRUNCATED_SUFFIX.getBytes(StandardCharsets.UTF_8);
        int budget = Math.max(0, maxBytes - suffix.length);
        int safe = safeUtf8Length(bytes, Math.min(budget, bytes.length));
        byte[] out = Arrays.copyOf(bytes, safe + suffix.length);
        System.arraycopy(suffix, 0, out, safe, suffix.length);
        return new String(out, StandardCharsets.UTF_8);
    }

    /**
     * 请求体大小校验：超 request-max-bytes 抛 {@link ToolNonRetryableException}（不可重试）。
     */
    public void checkRequestSize(int bytes) {
        int limit = properties.getSecurity().getRequestMaxBytes();
        if (bytes > limit) {
            throw new ToolNonRetryableException(
                    "request body too large: " + bytes + " > " + limit + " bytes",
                    null, null, "execute");
        }
    }

    /**
     * 找出 bytes[0,len) 中不截断多字节 UTF-8 字符的最大安全长度。
     */
    private int safeUtf8Length(byte[] b, int len) {
        int i = len;
        while (i > 0 && (b[i - 1] & 0xC0) == 0x80) {
            i--; // 跳过续字节
        }
        if (i <= 0) {
            return 0;
        }
        int lead = b[i - 1] & 0xFF;
        int expected = switch (lead >> 4) {
            case 0, 1, 2, 3, 4, 5, 6, 7 -> 1;   // 0xxxxxxx
            case 8, 9, 10, 11 -> 1;             // 续字节落到此处视为非法，丢弃
            case 12, 13 -> 2;                    // 110xxxxx
            case 14 -> 3;                        // 1110xxxx
            default -> 4;                        // 11110xxx
        };
        int have = len - (i - 1);
        return have < expected ? i - 1 : len;
    }
}
