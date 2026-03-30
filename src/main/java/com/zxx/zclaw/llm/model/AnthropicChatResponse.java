package com.zxx.zclaw.llm.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * Anthropic Messages API response body.
 */
public class AnthropicChatResponse {

    private String id;
    private String type;
    private String role;
    private List<ContentBlock> content;
    private String model;
    private String stop_reason;
    private Usage usage;

    public String getId() { return id; }
    public String getType() { return type; }
    public String getRole() { return role; }
    public List<ContentBlock> getContent() { return content; }
    public String getModel() { return model; }
    public String getStopReason() { return stop_reason; }
    public Usage getUsage() { return usage; }

    public static class Usage {
        private int input_tokens;
        private int output_tokens;

        @SerializedName("cache_read_input_tokens")
        private int cacheReadInputTokens;

        @SerializedName("cache_creation_input_tokens")
        private int cacheCreationInputTokens;

        public int getInputTokens() { return input_tokens; }
        public int getOutputTokens() { return output_tokens; }

        public int getCacheReadInputTokens() { return cacheReadInputTokens; }
        public int getCacheCreationInputTokens() { return cacheCreationInputTokens; }
    }

    /**
     * Anthropic error response.
     */
    public static class ErrorResponse {
        private ErrorDetail error;

        public ErrorDetail getError() { return error; }

        public static class ErrorDetail {
            private String type;
            private String message;

            public String getType() { return type; }
            public String getMessage() { return message; }
        }
    }
}
