package com.zify.chat.domain;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * opaque 游标编解码（Controller 层使用，common 的 CursorPageResult 不改）。
 * <p>
 * 编码：Base64Url( ISO-8601(timestamp) + "#" + id )；无更多数据时返回 null。
 * 解码：拆出 timestamp 与 id，填入 CursorPageRequest 的 Keyset 条件。
 */
public final class CursorCodec {

    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private static final String SEPARATOR = "#";

    private CursorCodec() {
    }

    public static String encode(LocalDateTime timestamp, String id) {
        if (timestamp == null || id == null || id.isBlank()) {
            return null;
        }
        String raw = timestamp.format(TS_FORMAT) + SEPARATOR + id;
        return ENCODER.encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * @return 解码结果；入参为空或格式非法时返回 null（视为首页 / 无游标）
     */
    public static DecodedCursor decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String raw = new String(DECODER.decode(cursor), StandardCharsets.UTF_8);
            int sep = raw.indexOf(SEPARATOR);
            if (sep < 0) {
                return null;
            }
            LocalDateTime timestamp = LocalDateTime.parse(raw.substring(0, sep), TS_FORMAT);
            String id = raw.substring(sep + 1);
            return new DecodedCursor(timestamp, id);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * 解码后的游标：Keyset 的时间戳分量 + id 分量。
     */
    public static final class DecodedCursor {
        private final LocalDateTime timestamp;
        private final String id;

        public DecodedCursor(LocalDateTime timestamp, String id) {
            this.timestamp = timestamp;
            this.id = id;
        }

        public LocalDateTime getTimestamp() {
            return timestamp;
        }

        public String getId() {
            return id;
        }
    }
}
