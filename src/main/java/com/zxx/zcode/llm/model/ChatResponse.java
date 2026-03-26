package com.zxx.zcode.llm.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * OpenAI-compatible chat completion response.
 */
public class ChatResponse {

    private String id;
    private String object;
    private List<Choice> choices;
    private Usage usage;

    public String getContent() {
        if (choices != null && !choices.isEmpty()) {
            return choices.get(0).getMessage().getContent();
        }
        return null;
    }

    public List<ToolCall> getToolCalls() {
        if (choices != null && !choices.isEmpty()) {
            return choices.get(0).getMessage().getToolCalls();
        }
        return null;
    }

    public boolean hasToolCalls() {
        List<ToolCall> calls = getToolCalls();
        return calls != null && !calls.isEmpty();
    }

    public Message getAssistantMessage() {
        if (choices != null && !choices.isEmpty()) {
            Choice.MessageBody mb = choices.get(0).getMessage();
            return Message.assistant(mb.getContent(), mb.getToolCalls());
        }
        return Message.assistant("", null);
    }

    public Usage getUsage() { return usage; }

    public static class Choice {
        private int index;
        private MessageBody message;

        @SerializedName("finish_reason")
        private String finishReason;

        public MessageBody getMessage() { return message; }
        public String getFinishReason() { return finishReason; }

        public static class MessageBody {
            private String role;
            private String content;

            @SerializedName("tool_calls")
            private List<ToolCall> toolCalls;

            public String getRole() { return role; }
            public String getContent() { return content; }
            public List<ToolCall> getToolCalls() { return toolCalls; }
        }
    }

    public static class Usage {
        @SerializedName("prompt_tokens")
        private int promptTokens;

        @SerializedName("completion_tokens")
        private int completionTokens;

        @SerializedName("total_tokens")
        private int totalTokens;

        @SerializedName("prompt_tokens_details")
        private PromptTokensDetails promptTokensDetails;

        public int getPromptTokens() { return promptTokens; }
        public int getCompletionTokens() { return completionTokens; }
        public int getTotalTokens() { return totalTokens; }

        /** Tokens served from prompt cache (OpenAI-style {@code prompt_tokens_details.cached_tokens}). */
        public int getCachedPromptTokens() {
            return promptTokensDetails != null ? promptTokensDetails.getCachedTokens() : 0;
        }

        public static class PromptTokensDetails {
            @SerializedName("cached_tokens")
            private int cachedTokens;

            public int getCachedTokens() { return cachedTokens; }
        }
    }
}
