package com.zify.common.exception;

/**
 * Backward-compatible alias for BusinessException.
 */
@Deprecated
public class BizException extends BusinessException {

    public BizException(ErrorCode errorCode) {
        super(errorCode);
    }

    public BizException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public BizException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }

    public BizException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}
