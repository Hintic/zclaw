package com.zxx.zcode.llm.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Map;

/**
 * OpenAI-compatible chat completion request.
 */
public class ChatRequest {

    private String model;
    private List<Message> messages;
    private List<ToolDefinition> tools;

    @SerializedName("tool_choice")
    private String toolChoice;

    private Double temperature;

    private Boolean stream;

    public ChatRequest(String model, List<Message> messages, List<ToolDefinition> tools) {
        this.model = model;
        this.messages = messages;
        this.tools = (tools != null && !tools.isEmpty()) ? tools : null;
        this.toolChoice = this.tools != null ? "auto" : null;
        this.temperature = 0.0;
        this.stream = false;
    }

    public void setStream(boolean stream) {
        this.stream = stream;
    }

    /**
     * Tool definition in OpenAI format.
     */
    public static class ToolDefinition {
        private final String type = "function";
        private FunctionDef function;

        public ToolDefinition(String name, String description, Map<String, Object> parameters) {
            this.function = new FunctionDef(name, description, parameters);
        }

        public String getName() { return function.name; }
        public String getDescription() { return function.description; }
        public Map<String, Object> getParameters() { return function.parameters; }

        public static class FunctionDef {
            private String name;
            private String description;
            private Map<String, Object> parameters;

            public FunctionDef(String name, String description, Map<String, Object> parameters) {
                this.name = name;
                this.description = description;
                this.parameters = parameters;
            }
        }
    }
}
