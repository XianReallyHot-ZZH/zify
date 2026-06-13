package com.zify.model.api.dto.provider;

/**
 * 供应商 API Key 响应（遮罩或明文）
 */
public class ProviderApiKeyResponse {

    private String maskedApiKey;
    private String decryptedApiKey;

    public String getMaskedApiKey() {
        return maskedApiKey;
    }

    public void setMaskedApiKey(String maskedApiKey) {
        this.maskedApiKey = maskedApiKey;
    }

    public String getDecryptedApiKey() {
        return decryptedApiKey;
    }

    public void setDecryptedApiKey(String decryptedApiKey) {
        this.decryptedApiKey = decryptedApiKey;
    }
}
