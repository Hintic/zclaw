package com.zxx.zcode.tool;

import java.util.Map;

/**
 * Interface for all agent tools.
 */
public interface Tool {

    /** Tool name used in LLM function calling (e.g. "read_file"). */
    String name();

    /** Human-readable description for the LLM. */
    String description();

    /** JSON Schema for the tool's parameters (OpenAI function calling format). */
    Map<String, Object> parameters();

    /**
     * Execute the tool with the given arguments.
     * @param args parsed JSON arguments as a map
     * @return the tool's output as a string
     */
    String execute(Map<String, Object> args);
}
