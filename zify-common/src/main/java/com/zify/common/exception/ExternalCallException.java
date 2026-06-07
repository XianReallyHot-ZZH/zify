package com.zify.common.exception;

/**
 * Base exception for external system calls.
 */
public class ExternalCallException extends RuntimeException {

    private final String provider;
    private final String scenario;
    private final boolean retryable;

    public ExternalCallException(String provider, String scenario, boolean retryable, String message) {
        super(message);
        this.provider = provider;
        this.scenario = scenario;
        this.retryable = retryable;
    }

    public ExternalCallException(String provider, String scenario, boolean retryable, String message, Throwable cause) {
        super(message, cause);
        this.provider = provider;
        this.scenario = scenario;
        this.retryable = retryable;
    }

    public String getProvider() {
        return provider;
    }

    public String getScenario() {
        return scenario;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
