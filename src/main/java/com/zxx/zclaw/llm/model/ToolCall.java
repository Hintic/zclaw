package com.zxx.zclaw.llm.model;

import com.google.gson.annotations.SerializedName;

/**
 * Represents a tool call from the LLM response.
 */
public class ToolCall {

    private String id;
    private String type;
    private Function function;

    public String getId() { return id; }
    public String getType() { return type; }
    public Function getFunction() { return function; }

    public String getFunctionName() {
        return function != null ? function.getName() : null;
    }

    public String getFunctionArguments() {
        return function != null ? function.getArguments() : null;
    }

    public static class Function {
        private String name;
        private String arguments;

        public String getName() { return name; }
        public String getArguments() { return arguments; }
    }

    @Override
    public String toString() {
        return "ToolCall{id='" + id + "', function=" +
                (function != null ? function.getName() : "null") + "}";
    }
}
