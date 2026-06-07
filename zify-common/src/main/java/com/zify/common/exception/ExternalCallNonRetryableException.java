package com.zify.common.exception;

/**
 * Non-retryable external call exception.
 */
public class ExternalCallNonRetryableException extends ExternalCallException {

    public ExternalCallNonRetryableException(String provider, String scenario, String message) {
        super(provider, scenario, false, message);
    }

    public ExternalCallNonRetryableException(String provider, String scenario, String message, Throwable cause) {
        super(provider, scenario, false, message, cause);
    }
}
