package com.zify.common.exception;

/**
 * Cancelled external call exception.
 */
public class ExternalCallCancelledException extends ExternalCallException {

    public ExternalCallCancelledException(String provider, String scenario, String message) {
        super(provider, scenario, false, message);
    }

    public ExternalCallCancelledException(String provider, String scenario, String message, Throwable cause) {
        super(provider, scenario, false, message, cause);
    }
}
