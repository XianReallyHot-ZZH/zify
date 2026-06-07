package com.zify.common.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * LLM-related shared configuration.
 */
@Component
@ConfigurationProperties(prefix = "zify.llm")
public class LlmProperties {

    private ProviderDefaults providerDefaults = new ProviderDefaults();

    public ProviderDefaults getProviderDefaults() {
        return providerDefaults;
    }

    public void setProviderDefaults(ProviderDefaults providerDefaults) {
        this.providerDefaults = providerDefaults;
    }

    public static class ProviderDefaults {

        private Integer maxConcurrent = 20;
        private Duration acquireTimeout = Duration.ofSeconds(2);

        public Integer getMaxConcurrent() {
            return maxConcurrent;
        }

        public void setMaxConcurrent(Integer maxConcurrent) {
            this.maxConcurrent = maxConcurrent;
        }

        public Duration getAcquireTimeout() {
            return acquireTimeout;
        }

        public void setAcquireTimeout(Duration acquireTimeout) {
            this.acquireTimeout = acquireTimeout;
        }
    }
}
