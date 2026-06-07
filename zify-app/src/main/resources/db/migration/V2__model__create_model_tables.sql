-- 模型供应商配置表
CREATE TABLE `model_provider` (
    `id`            CHAR(36)     NOT NULL COMMENT '供应商 ID',
    `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间，UTC',
    `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间，UTC',
    `is_deleted`    TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常，1 已删除',
    `created_by`    CHAR(36)     NULL     COMMENT '创建人用户ID',
    `updated_by`    CHAR(36)     NULL     COMMENT '更新人用户ID',

    `name`          VARCHAR(128) NOT NULL COMMENT '供应商名称',
    `provider_type` VARCHAR(32)  NOT NULL COMMENT '供应商类型：OPENAI / ANTHROPIC / OPENAI_COMPATIBLE',
    `api_key`       VARCHAR(512) NULL     COMMENT 'API Key（AES 加密存储）',
    `base_url`      VARCHAR(512) NOT NULL COMMENT 'API Base URL',
    `extra_config`  JSON         NULL     COMMENT '供应商特有配置',
    `status`        VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE / INACTIVE',

    `active_name`   VARCHAR(128)
        GENERATED ALWAYS AS (
            CASE WHEN `is_deleted` = 0 THEN `name` ELSE NULL END
        ) STORED COMMENT '未删除名称唯一键辅助列',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_mp_active_name` (`active_name`),
    KEY `idx_mp_type_deleted_created_id`
        (`provider_type`, `is_deleted`, `created_at` DESC, `id` DESC),
    KEY `idx_mp_status_deleted_created_id`
        (`status`, `is_deleted`, `created_at` DESC, `id` DESC)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='模型供应商配置';

-- 模型配置表
CREATE TABLE `model` (
    `id`              CHAR(36)     NOT NULL COMMENT '模型 ID',
    `created_at`      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间，UTC',
    `updated_at`      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间，UTC',
    `is_deleted`      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常，1 已删除',

    `provider_id`     CHAR(36)     NOT NULL COMMENT '所属供应商 ID',
    `model_name`      VARCHAR(255) NOT NULL COMMENT '模型标识',
    `display_name`    VARCHAR(128) NULL     COMMENT '显示名称',
    `model_type`      VARCHAR(32)  NOT NULL COMMENT '模型类型：LLM / EMBEDDING / RERANK',
    `enabled`         TINYINT      NOT NULL DEFAULT 1 COMMENT '是否启用：0 禁用，1 启用',
    `default_params`  JSON         NULL     COMMENT '默认调用参数',

    `active_provider_model` VARCHAR(292)
        GENERATED ALWAYS AS (
            CASE
                WHEN `is_deleted` = 0 THEN CONCAT(`provider_id`, '#', `model_name`)
                ELSE NULL
            END
        ) STORED COMMENT '未删除供应商+模型名唯一键辅助列',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_model_active_provider_model` (`active_provider_model`),
    KEY `idx_model_provider_deleted_created_id`
        (`provider_id`, `is_deleted`, `created_at` DESC, `id` DESC),
    KEY `idx_model_type_enabled_deleted`
        (`model_type`, `enabled`, `is_deleted`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='模型配置';
