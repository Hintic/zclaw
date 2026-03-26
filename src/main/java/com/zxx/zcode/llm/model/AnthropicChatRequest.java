package com.zxx.zcode.llm.model;

import java.util.List;
import java.util.Map;

/**
 * Anthropic Messages API request body.
 */
public class AnthropicChatRequest {

    private String model;
    private int max_tokens;
    private String system;
    private List<AnthropicMessage> messages;
    private List<Object> tools;

    public AnthropicChatRequest(String model, int maxTokens, String system,
                                 List<AnthropicMessage> messages, List<Object> tools) {
        this.model = model;
        this.max_tokens = maxTokens;
        this.system = system;
        this.messages = messages;
        this.tools = (tools != null && !tools.isEmpty()) ? tools : null;
    }

    /**
     * A message in Anthropic format.
     */
    public static class AnthropicMessage {
        private String role;
        private Object content; // String or List<ContentBlock/Map>

        public AnthropicMessage(String role, Object content) {
            this.role = role;
            this.content = content;
        }

        public String getRole() { return role; }
        public Object getContent() { return content; }
    }

    /**
     * Client-side tool definition in Anthropic format.
     */
    public static class AnthropicToolDef {
        private String name;
        private String description;
        private Map<String, Object> input_schema;

        public AnthropicToolDef(String name, String description, Map<String, Object> inputSchema) {
            this.name = name;
            this.description = description;
            this.input_schema = inputSchema;
        }
    }

    /**
     * Server-side web search tool definition.
     */
    public static class WebSearchToolDef {
        private final String type = "web_search_20250305";
        private final String name = "web_search";
        private int max_uses;
        private List<String> allowed_domains;
        private List<String> blocked_domains;

        public WebSearchToolDef(int maxUses, List<String> allowedDomains, List<String> blockedDomains) {
            this.max_uses = maxUses;
            if (allowedDomains != null && !allowedDomains.isEmpty()) {
                this.allowed_domains = allowedDomains;
            }
            if (blockedDomains != null && !blockedDomains.isEmpty()) {
                this.blocked_domains = blockedDomains;
            }
        }
    }
}
