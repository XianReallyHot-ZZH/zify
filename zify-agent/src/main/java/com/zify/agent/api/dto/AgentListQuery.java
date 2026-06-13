package com.zify.agent.api.dto;

import com.zify.common.web.PageRequest;

/**
 * Agent 列表查询（小表 OFFSET 分页）。
 */
public class AgentListQuery extends PageRequest {

    /** 名称模糊（前缀匹配，禁前导 %）。 */
    private String name;
    private String agentType;
    private String status;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAgentType() {
        return agentType;
    }

    public void setAgentType(String agentType) {
        this.agentType = agentType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
