package com.zxx.zcode.llm.model;

/**
 * Unified tool call info extracted from LLM response (works for both OpenAI and Anthropic).
 */
public class ToolCallInfo {

    private final String id;
    private final String name;
    private final String argumentsJson;

    public ToolCallInfo(String id, String name, String argumentsJson) {
        this.id = id;
        this.name = name;
        this.argumentsJson = argumentsJson;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getArgumentsJson() { return argumentsJson; }

    @Override
    public String toString() {
        return "ToolCallInfo{id='" + id + "', name='" + name + "'}";
    }
}
