package com.zify.common.exception;

/**
 * Retryable external call exception.
 */
public class ExternalCallRetryableException extends ExternalCallException {

    public ExternalCallRetryableException(String provider, String scenario, String message) {
        super(provider, scenario, true, message);
    }

    public ExternalCallRetryableException(String provider, String scenario, String message, Throwable cause) {
        super(provider, scenario, true, message, cause);
    }
}
