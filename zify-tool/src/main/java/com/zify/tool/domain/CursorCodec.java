package com.zify.tool.domain;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * opaque 游标编解码（tool 模块本地副本，tool 不依赖 chat；逻辑同 P1 CursorCodec）。
 * 编码：Base64Url( ISO-8601(timestamp) + "#" + id )；无更多数据时返回 null。
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
