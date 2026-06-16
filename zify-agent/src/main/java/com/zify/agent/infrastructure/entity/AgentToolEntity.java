package com.zify.agent.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zify.common.persistence.entity.BaseEntity;

/**
 * Agent 与工具关联实体（映射 agent_tool 表，归属 agent 模块）。
 * <p>
 * 关联表无 created_by/updated_by、无 enabled。active_agent_tool 是 generated column，不映射为字段（§3.4）。
 */
@TableName("agent_tool")
public class AgentToolEntity extends BaseEntity {

    private String agentId;
    private String toolId;

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public String getToolId() {
        return toolId;
    }

    public void setToolId(String toolId) {
        this.toolId = toolId;
    }
}
