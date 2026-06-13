package com.zify.model.domain;

/**
 * 供应商类型枚举，仅用于策略路由，不侵入 Entity / DTO / 数据库
 */
public enum ProviderType {

    ANTHROPIC,
    OPENAI,
    OPENAI_COMPATIBLE;

    public static ProviderType fromString(String value) {
        if (value == null) {
            throw new IllegalArgumentException("providerType must not be null");
        }
        return switch (value.toUpperCase()) {
            case "ANTHROPIC" -> ANTHROPIC;
            case "OPENAI" -> OPENAI;
            case "OPENAI_COMPATIBLE" -> OPENAI_COMPATIBLE;
            default -> throw new IllegalArgumentException("Unknown providerType: " + value);
        };
    }
}
