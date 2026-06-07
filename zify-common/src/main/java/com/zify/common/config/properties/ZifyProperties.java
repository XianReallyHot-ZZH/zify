package com.zify.common.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Root Zify configuration properties.
 */
@Component
@ConfigurationProperties(prefix = "zify")
public class ZifyProperties {

    /**
     * Reserved root switch for future global settings.
     */
    private Boolean enabled = Boolean.TRUE;

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
}
