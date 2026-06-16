package com.zify.tool.api.dto;

import java.util.Map;

/**
 * 工具测试调用请求。
 */
public class ToolTestRequest {

    private Map<String, Object> args;

    public Map<String, Object> getArgs() { return args; }
    public void setArgs(Map<String, Object> args) { this.args = args; }
}
