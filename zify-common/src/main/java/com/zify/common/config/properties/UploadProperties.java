package com.zify.common.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

/**
 * Upload-related configuration.
 */
@Component
@ConfigurationProperties(prefix = "zify.upload")
public class UploadProperties {

    private String dir = "/data/uploads";
    private DataSize maxSize = DataSize.ofMegabytes(100);

    public String getDir() {
        return dir;
    }

    public void setDir(String dir) {
        this.dir = dir;
    }

    public DataSize getMaxSize() {
        return maxSize;
    }

    public void setMaxSize(DataSize maxSize) {
        this.maxSize = maxSize;
    }
}
