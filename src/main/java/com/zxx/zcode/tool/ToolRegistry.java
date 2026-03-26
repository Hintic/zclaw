package com.zxx.zcode.tool;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.zxx.zcode.llm.model.ChatRequest;

import java.util.*;

public class ToolRegistry {

    private static final int MAX_RESULT_LENGTH = 10000;

    private final Map<String, Tool> tools = new LinkedHashMap<>();
    private final Gson gson = new Gson();

    public void register(Tool tool) {
        tools.put(tool.name(), tool);
    }

    public List<ChatRequest.ToolDefinition> getToolDefinitions() {
        List<ChatRequest.ToolDefinition> defs = new ArrayList<>();
        for (Tool tool : tools.values()) {
            defs.add(new ChatRequest.ToolDefinition(tool.name(), tool.description(), tool.parameters()));
        }
        return defs;
    }

    public String execute(String toolName, String argumentsJson) {
        Tool tool = tools.get(toolName);
        if (tool == null) {
            return "Error: unknown tool '" + toolName + "'";
        }
        try {
            Map<String, Object> args = gson.fromJson(
                    argumentsJson,
                    new TypeToken<Map<String, Object>>() {}.getType()
            );
            if (args == null) {
                args = Collections.emptyMap();
            }
            String result = tool.execute(args);
            if (result.length() > MAX_RESULT_LENGTH) {
                result = result.substring(0, MAX_RESULT_LENGTH)
                        + "\n... [truncated, " + result.length() + " total chars]";
            }
            return result;
        } catch (Exception e) {
            return "Error executing tool '" + toolName + "': " + e.getMessage();
        }
    }

    public boolean hasTool(String name) {
        return tools.containsKey(name);
    }

    public Collection<Tool> allTools() {
        return tools.values();
    }
}
