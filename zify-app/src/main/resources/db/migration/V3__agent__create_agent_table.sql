-- Agent 主表（P1 只用 REACT 类型；workflow_id 为 P4 WORKFLOW Agent 预留，P1 恒 NULL）
CREATE TABLE `agent` (
    `id`            CHAR(36)     NOT NULL COMMENT 'Agent ID',
    `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间，UTC',
    `updated_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间，UTC',
    `is_deleted`    TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常，1 已删除',
    `created_by`    CHAR(36)     NULL     COMMENT '创建人用户ID，一期无登录可为空',
    `updated_by`    CHAR(36)     NULL     COMMENT '更新人用户ID，一期无登录可为空',

    `name`          VARCHAR(128) NOT NULL COMMENT 'Agent 名称',
    `description`   VARCHAR(512) NULL     COMMENT 'Agent 描述',
    `agent_type`    VARCHAR(32)  NOT NULL COMMENT 'Agent 类型：REACT / WORKFLOW',
    `status`        VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE / INACTIVE',
    `system_prompt` MEDIUMTEXT   NULL     COMMENT 'System Prompt',
    `model_id`      CHAR(36)     NULL     COMMENT '绑定的 LLM 模型 ID（REACT 必填，引用 model.id）',
    `workflow_id`   CHAR(36)     NULL     COMMENT '绑定的工作流 ID（WORKFLOW 类型用，P4 启用）',

    `active_name`   VARCHAR(128)
        GENERATED ALWAYS AS (
            CASE WHEN `is_deleted` = 0 THEN `name` ELSE NULL END
        ) STORED COMMENT '未删除 Agent 名称唯一键辅助列',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_agent_active_name` (`active_name`),
    KEY `idx_agent_type_deleted_created_id`
        (`agent_type`, `is_deleted`, `created_at` DESC, `id` DESC),
    KEY `idx_agent_model_deleted_id`
        (`model_id`, `is_deleted`, `id` DESC),
    KEY `idx_agent_created_at` (`created_at`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Agent';
