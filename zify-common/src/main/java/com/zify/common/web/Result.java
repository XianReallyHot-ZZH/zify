package com.zify.common.web;

/**
 * 统一响应体
 *
 * @param <T> 业务数据类型
 */
public class Result<T> {

    private final int code;
    private final String message;
    private final T data;

    public Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    // ── 成功 ──────────────────────────────────────────────

    public static <T> Result<T> ok() {
        return new Result<>(200, "success", null);
    }

    public static <T> Result<T> ok(T data) {
        return new Result<>(200, "success", data);
    }

    public static <T> Result<T> ok(String message, T data) {
        return new Result<>(200, message, data);
    }

    // ── 失败 ──────────────────────────────────────────────

    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }

    // ── Getter ────────────────────────────────────────────

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public T getData() {
        return data;
    }
}
