package com.zxx.zclaw.llm.model;

import java.util.List;
import java.util.Map;

/**
 * Represents a content block in Anthropic Messages API response.
 * Types: "text", "tool_use", "server_tool_use", "web_search_tool_result", "thinking"
 */
public class ContentBlock {

    private String type;
    private String id;
    private String name;
    private String text;
    private String thinking;  // for thinking block type
    private Map<String, Object> input;
    private List<SearchResultEntry> content;

    // --- for web_search_tool_result ---
    private String tool_use_id;

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public String getThinking() { return thinking; }
    public void setThinking(String thinking) { this.thinking = thinking; }

    public Map<String, Object> getInput() { return input; }
    public void setInput(Map<String, Object> input) { this.input = input; }

    public List<SearchResultEntry> getContent() { return content; }
    public void setContent(List<SearchResultEntry> content) { this.content = content; }

    public String getToolUseId() { return tool_use_id; }
    public void setToolUseId(String toolUseId) { this.tool_use_id = toolUseId; }

    /**
     * A single entry in web_search_tool_result.content array.
     */
    public static class SearchResultEntry {
        private String type;
        private String url;
        private String title;
        private String encrypted_content;
        private String page_age;

        public String getType() { return type; }
        public String getUrl() { return url; }
        public String getTitle() { return title; }
        public String getEncryptedContent() { return encrypted_content; }
        public String getPageAge() { return page_age; }
    }

    @Override
    public String toString() {
        return "ContentBlock{type='" + type + "', id='" + id + "', name='" + name + "'}";
    }
}
