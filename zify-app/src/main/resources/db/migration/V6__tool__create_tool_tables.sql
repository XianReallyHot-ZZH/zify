-- MCP Server 连接配置表
CREATE TABLE `mcp_server` (
    `id`                CHAR(36)     NOT NULL COMMENT 'MCP Server ID',
    `created_at`        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间，UTC',
    `updated_at`        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间，UTC',
    `is_deleted`        TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常，1 已删除',
    `created_by`        CHAR(36)     NULL     COMMENT '创建人用户ID，一期无登录可为空',
    `updated_by`        CHAR(36)     NULL     COMMENT '更新人用户ID，一期无登录可为空',

    `name`              VARCHAR(128) NOT NULL COMMENT 'MCP Server 名称（用户自定义，如"GitHub MCP"）',
    `description`       VARCHAR(512) NULL     COMMENT '描述',
    `base_url`          VARCHAR(512) NOT NULL COMMENT '连接地址（Streamable HTTP 或 SSE 端点）',
    `transport_type`    VARCHAR(32)  NOT NULL DEFAULT 'STREAMABLE_HTTP' COMMENT '传输方式：STREAMABLE_HTTP / SSE',
    `auth_type`         VARCHAR(32)  NOT NULL DEFAULT 'NONE' COMMENT '认证方式：NONE / API_KEY / BEARER（OAuth 一期不做）',
    `auth_config`       VARCHAR(1024) NULL    COMMENT '认证凭据（加密 JSON 整体存储，复用 SecretEncryptor；NONE 时为 NULL）',
    `enabled`           TINYINT      NOT NULL DEFAULT 1 COMMENT '管理员启用开关：0 禁用（断开连接且不重连），1 启用',
    `status`            VARCHAR(32)  NOT NULL DEFAULT 'OFFLINE' COMMENT '连接健康度：ONLINE / OFFLINE / ERROR',
    `status_message`    VARCHAR(512) NULL     COMMENT '连接状态详情（如最近一次错误信息）',
    `last_connected_at` DATETIME(3)  NULL     COMMENT '最近一次连接成功（ONLINE）时间',

    `active_name`       VARCHAR(128)
        GENERATED ALWAYS AS (
            CASE WHEN `is_deleted` = 0 THEN `name` ELSE NULL END
        ) STORED COMMENT '未删除名称唯一键辅助列',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ms_active_name` (`active_name`),
    KEY `idx_ms_enabled_deleted_created_id`
        (`enabled`, `is_deleted`, `created_at` DESC, `id` DESC),
    KEY `idx_ms_status_deleted_id` (`status`, `is_deleted`, `id` DESC),
    KEY `idx_ms_created_at` (`created_at`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='MCP Server 连接配置';


-- 统一工具定义表（HTTP 工具 + MCP 工具；P4 再加 Workflow-as-Tool）
CREATE TABLE `tool` (
    `id`              CHAR(36)     NOT NULL COMMENT '工具 ID',
    `created_at`      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间，UTC',
    `updated_at`      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间，UTC',
    `is_deleted`      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常，1 已删除',
    `created_by`      CHAR(36)     NULL     COMMENT '创建人用户ID，一期无登录可为空',
    `updated_by`      CHAR(36)     NULL     COMMENT '更新人用户ID，一期无登录可为空',

    `name`            VARCHAR(128) NOT NULL COMMENT '工具名（LLM 可见，全局未删除唯一；HTTP 用户起名，MCP 冲突时加 server 前缀 mcpServerName__toolName）',
    `description`     VARCHAR(512) NULL     COMMENT '工具描述（喂给 LLM 的工具说明，OpenAPI 导入时取 summary/description，超长截断）',
    `source_type`     VARCHAR(32)  NOT NULL COMMENT '工具来源：HTTP / MCP / WORKFLOW（WORKFLOW=Workflow-as-Tool，P4 启用）',
    `mcp_server_id`   CHAR(36)     NULL     COMMENT '来源 MCP Server ID（仅 source_type=MCP；HTTP/WORKFLOW 为 NULL）',
    `input_schema`    TEXT         NULL     COMMENT '工具输入参数 JSON Schema（字符串），定义时一次性生成入库，运行时原样透传给 LLM',

    `endpoint`        VARCHAR(512) NULL     COMMENT 'HTTP 工具完整请求地址（baseUrl+path 或模板，含 {param} 占位；仅 HTTP）',
    `method`          VARCHAR(16)  NULL     COMMENT 'HTTP 方法：GET/POST/PUT/DELETE/PATCH（仅 HTTP）',
    `config_json`     JSON         NULL     COMMENT 'HTTP 工具配置：params_mapping/headers_template/body_template（仅 HTTP）',
    `auth_config`     VARCHAR(1024) NULL    COMMENT 'HTTP 工具鉴权凭据（加密 JSON 整体存储；仅 HTTP，NONE 时为 NULL）',

    `timeout_seconds` INT          NULL     COMMENT '单次请求超时（秒），NULL 用全局默认 request-default=30s',
    `idempotent`      TINYINT      NOT NULL DEFAULT 0 COMMENT '是否幂等(0否1是)：HTTP 按 method 默认推断，MCP 默认0；驱动重试策略',
    `enabled`         TINYINT      NOT NULL DEFAULT 1 COMMENT '是否启用：0 禁用，1 启用；禁用后 listAvailableTools 不可见',

    `active_name`     VARCHAR(128)
        GENERATED ALWAYS AS (
            CASE WHEN `is_deleted` = 0 THEN `name` ELSE NULL END
        ) STORED COMMENT '未删除工具名唯一键辅助列',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tool_active_name` (`active_name`),
    KEY `idx_tool_source_deleted_created_id`
        (`source_type`, `is_deleted`, `created_at` DESC, `id` DESC),
    KEY `idx_tool_mcpserver_deleted_id`
        (`mcp_server_id`, `is_deleted`, `id` DESC),
    KEY `idx_tool_created_at` (`created_at`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='统一工具定义';


-- 工具调用日志表（大表，执行点即记录点；对话/工作流/手动测试都经此）
CREATE TABLE `tool_call_log` (
    `id`                    CHAR(36)     NOT NULL COMMENT '日志 ID',
    `created_at`            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '调用时间，UTC',
    `updated_at`            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间，UTC',
    `is_deleted`            TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常，1 已删除',

    `tool_id`               CHAR(36)     NOT NULL COMMENT '调用的工具 ID',
    `tool_name`             VARCHAR(128) NOT NULL COMMENT '工具名快照（工具可能被改名/删除，快照保证日志可读）',
    `source_type`           VARCHAR(32)  NOT NULL COMMENT '工具来源快照：HTTP / MCP / WORKFLOW',
    `mcp_server_id`         CHAR(36)     NULL     COMMENT 'MCP Server ID（仅 MCP 工具）',
    `agent_id`              CHAR(36)     NULL     COMMENT '触发调用的 Agent ID（对话场景；手动测试/工作流为 NULL）',
    `conversation_id`       CHAR(36)     NULL     COMMENT '触发调用的会话 ID（对话场景；手动测试/工作流为 NULL）',
    `workflow_run_id`       CHAR(36)     NULL     COMMENT '工作流运行 ID（P4 工作流场景；P2 恒 NULL）',
    `workflow_node_run_id`  CHAR(36)     NULL     COMMENT '工作流节点运行 ID（P4；P2 恒 NULL）',
    `turn`                  INT          NULL     COMMENT '对话 ReAct 循环轮次（1 起始；手动测试/工作流为 NULL）',
    `tool_call_id`          VARCHAR(128) NULL     COMMENT '模型生成的本次调用 ID，关联 message(TOOL) 的 metadata.toolCallId',
    `input`                 JSON         NULL     COMMENT '调用入参（args 的 JSON，截断后）',
    `output`                LONGTEXT     NULL     COMMENT '调用结果 output（截断后）',
    `status`                VARCHAR(32)  NOT NULL COMMENT '执行状态：SUCCESS / ERROR / TIMEOUT / CIRCUIT_OPEN / CANCELLED',
    `duration_ms`           INT          NOT NULL DEFAULT 0 COMMENT '执行耗时（毫秒）',
    `error`                 TEXT         NULL     COMMENT '失败时的精简错误信息（成功为 NULL；不含鉴权凭据）',

    PRIMARY KEY (`id`),
    KEY `idx_tcl_conv_deleted_created_id`
        (`conversation_id`, `is_deleted`, `created_at` DESC, `id` DESC),
    KEY `idx_tcl_agent_deleted_created_id`
        (`agent_id`, `is_deleted`, `created_at` DESC, `id` DESC),
    KEY `idx_tcl_tool_deleted_created_id`
        (`tool_id`, `is_deleted`, `created_at` DESC, `id` DESC),
    KEY `idx_tcl_created_at` (`created_at`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='工具调用日志';
