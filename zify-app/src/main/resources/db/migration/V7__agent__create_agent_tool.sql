-- Agent 与工具关联表（归属 agent 模块）
CREATE TABLE `agent_tool` (
    `id`          CHAR(36)     NOT NULL COMMENT '主键',
    `created_at`  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间，UTC',
    `updated_at`  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间，UTC',
    `is_deleted`  TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',

    `agent_id`    CHAR(36)     NOT NULL COMMENT 'Agent ID',
    `tool_id`     CHAR(36)     NOT NULL COMMENT '工具 ID',

    `active_agent_tool` VARCHAR(73)
        GENERATED ALWAYS AS (
            CASE WHEN `is_deleted` = 0 THEN CONCAT(`agent_id`, '#', `tool_id`) ELSE NULL END
        ) STORED COMMENT '未删除关联唯一键辅助列',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_at_active_agent_tool` (`active_agent_tool`),
    KEY `idx_at_agent_deleted_created_id`
        (`agent_id`, `is_deleted`, `created_at` DESC, `id` DESC),
    KEY `idx_at_tool_deleted_created_id`
        (`tool_id`, `is_deleted`, `created_at` DESC, `id` DESC)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Agent 与工具关联';
