package com.zify.common.exception;

/**
 * 业务异常，持有 ErrorCode，支持自定义 message 覆盖
 */
public class BizException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String detailMessage;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.detailMessage = null;
    }

    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.detailMessage = message;
    }

    public BizException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.detailMessage = message;
    }

    public BizException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
        this.detailMessage = null;
    }

    public int getCode() {
        return errorCode.getCode();
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    /**
     * 获取实际展示的消息。如果构造时传入了自定义 message 则使用自定义值，否则使用枚举默认值。
     */
    public String getDetailMessage() {
        return detailMessage != null ? detailMessage : errorCode.getMessage();
    }
}
