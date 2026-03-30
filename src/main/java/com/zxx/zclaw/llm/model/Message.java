package com.zxx.zclaw.llm.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Map;

/**
 * Represents a message in the conversation history.
 * Supports both OpenAI format (content as String) and Anthropic format (content as block array).
 */
public class Message {

    private String role;
    private Object content;

    @SerializedName("tool_calls")
    private List<ToolCall> toolCalls;

    @SerializedName("tool_call_id")
    private String toolCallId;

    private String name;

    /**
     * For Anthropic: stores raw content blocks from assistant response.
     * Transient so it's not serialized in OpenAI format.
     */
    private transient List<ContentBlock> anthropicContent;

    private Message() {}

    // ========== OpenAI factory methods ==========

    public static Message system(String content) {
        Message m = new Message();
        m.role = "system";
        m.content = content;
        return m;
    }

    public static Message user(String content) {
        Message m = new Message();
        m.role = "user";
        m.content = content;
        return m;
    }

    public static Message assistant(String content, List<ToolCall> toolCalls) {
        Message m = new Message();
        m.role = "assistant";
        m.content = content;
        m.toolCalls = (toolCalls != null && !toolCalls.isEmpty()) ? toolCalls : null;
        return m;
    }

    public static Message toolResult(String toolCallId, String content) {
        Message m = new Message();
        m.role = "tool";
        m.toolCallId = toolCallId;
        m.content = content;
        return m;
    }

    // ========== Anthropic factory methods ==========

    /**
     * Create an assistant message from Anthropic content blocks.
     * The raw blocks are preserved for sending back in subsequent requests.
     */
    public static Message anthropicAssistant(List<ContentBlock> contentBlocks) {
        Message m = new Message();
        m.role = "assistant";
        m.anthropicContent = contentBlocks;
        // Extract text for display purposes
        if (contentBlocks != null) {
            StringBuilder text = new StringBuilder();
            for (ContentBlock block : contentBlocks) {
                if ("text".equals(block.getType()) && block.getText() != null) {
                    text.append(block.getText());
                }
            }
            m.content = text.length() > 0 ? text.toString() : null;
        }
        return m;
    }

    /**
     * Create a tool result message in Anthropic format.
     * In Anthropic API, tool results are sent as user messages with content=[{type: "tool_result", ...}].
     */
    public static Message anthropicToolResult(List<Map<String, Object>> toolResults) {
        Message m = new Message();
        m.role = "user";
        m.content = toolResults;
        return m;
    }

    // ========== Getters ==========

    public String getRole() { return role; }

    /**
     * Get content as Object (may be String or List for Anthropic tool results).
     */
    public Object getContent() { return content; }

    /**
     * Get content as String. Returns null if content is not a String.
     */
    public String getContentAsString() {
        return content instanceof String ? (String) content : null;
    }

    public List<ToolCall> getToolCalls() { return toolCalls; }
    public String getToolCallId() { return toolCallId; }
    public List<ContentBlock> getAnthropicContent() { return anthropicContent; }

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    @Override
    public String toString() {
        String contentStr = content != null ?
                content.toString().substring(0, Math.min(content.toString().length(), 50)) : "null";
        return "Message{role='" + role + "', content='" + contentStr + "', toolCalls=" + toolCalls + "}";
    }
}
