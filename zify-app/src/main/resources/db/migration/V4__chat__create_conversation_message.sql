-- 对话会话表（含历史摘要列 summary_text / summary_covered_message_id）
CREATE TABLE `conversation` (
    `id`              CHAR(36)     NOT NULL COMMENT '会话 ID',
    `created_at`      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间，UTC',
    `updated_at`      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间，UTC',
    `is_deleted`      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常，1 已删除',
    `created_by`      CHAR(36)     NULL     COMMENT '创建人用户ID，一期无登录可为空',
    `updated_by`      CHAR(36)     NULL     COMMENT '更新人用户ID，一期无登录可为空',

    `title`           VARCHAR(128) NOT NULL COMMENT '会话标题，默认取 Agent 名称',
    `agent_id`        CHAR(36)     NOT NULL COMMENT '所属 Agent ID',
    `status`          VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE / ARCHIVED',
    `message_count`   INT          NOT NULL DEFAULT 0 COMMENT '消息数量（冗余，列表展示用）',
    `last_message_at` DATETIME(3)  NOT NULL COMMENT '最后一条消息时间（冗余，列表排序用；创建时置为创建时间）',
    `summary_text`               MEDIUMTEXT NULL COMMENT '历史摘要正文，覆盖到 summary_covered_message_id',
    `summary_covered_message_id` CHAR(36)   NULL COMMENT '摘要已覆盖到的最后一条消息 ID',

    PRIMARY KEY (`id`),
    KEY `idx_conv_deleted_lastmsg_id`
        (`is_deleted`, `last_message_at` DESC, `id` DESC),
    KEY `idx_conv_agent_deleted_created_id`
        (`agent_id`, `is_deleted`, `created_at` DESC, `id` DESC),
    KEY `idx_conv_created_at` (`created_at`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='对话会话';


-- 对话消息表（大表，无 created_by/updated_by，无 status）
CREATE TABLE `message` (
    `id`              CHAR(36)     NOT NULL COMMENT '消息 ID',
    `created_at`      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间，UTC',
    `updated_at`      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间，UTC',
    `is_deleted`      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常，1 已删除',

    `conversation_id` CHAR(36)     NOT NULL COMMENT '所属会话 ID',
    `role`            VARCHAR(32)  NOT NULL COMMENT '角色：USER / ASSISTANT / SYSTEM / TOOL',
    `content`         LONGTEXT     NOT NULL COMMENT '消息正文',
    `metadata`        JSON         NULL     COMMENT '消息元数据（仅 ASSISTANT）',

    PRIMARY KEY (`id`),
    KEY `idx_msg_conv_deleted_created_id`
        (`conversation_id`, `is_deleted`, `created_at` DESC, `id` DESC),
    KEY `idx_msg_created_at` (`created_at`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='对话消息';
