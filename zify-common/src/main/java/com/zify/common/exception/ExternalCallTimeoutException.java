package com.zify.common.exception;

/**
 * External call timeout exception.
 */
public class ExternalCallTimeoutException extends ExternalCallException {

    public ExternalCallTimeoutException(String provider, String scenario, String message) {
        super(provider, scenario, true, message);
    }

    public ExternalCallTimeoutException(String provider, String scenario, String message, Throwable cause) {
        super(provider, scenario, true, message, cause);
    }
}
