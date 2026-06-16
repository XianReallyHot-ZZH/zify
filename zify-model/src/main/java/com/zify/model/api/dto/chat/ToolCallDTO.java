package com.zify.model.api.dto.chat;

/**
 * 模型返回的一次工具调用（中立 DTO）。
 */
public class ToolCallDTO {

    /** 模型生成的调用 ID（与 TOOL 消息的 toolCallId 配对）。 */
    private String id;
    private String name;
    /** 入参 JSON 字符串。 */
    private String args;

    public ToolCallDTO() {
    }

    public ToolCallDTO(String id, String name, String args) {
        this.id = id;
        this.name = name;
        this.args = args;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getArgs() {
        return args;
    }

    public void setArgs(String args) {
        this.args = args;
    }
}
