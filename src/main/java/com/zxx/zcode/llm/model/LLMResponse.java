package com.zxx.zcode.llm.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Unified LLM response model that works for both OpenAI and Anthropic APIs.
 */
public class LLMResponse {

    private String textContent;
    private List<ToolCallInfo> toolCalls;
    private List<WebSearchResult> webSearchResults;
    private int inputTokens;
    private int outputTokens;
    /** Prompt tokens read from cache (OpenAI cached_tokens, Anthropic cache_read_input_tokens). */
    private int cacheReadTokens;
    /** Anthropic cache_creation_input_tokens; 0 for OpenAI. */
    private int cacheCreationTokens;
    /**
     * Sum of input-side tokens used as denominator for cache hit rate for this response.
     * OpenAI: {@code prompt_tokens}. Anthropic: {@code input + cache_read + cache_creation}.
     * If 0, session accounting falls back to {@code input + cacheRead + cacheCreation} or input only.
     */
    private int cacheHitDenominator;
    private Message rawAssistantMessage;

    public LLMResponse() {
        this.toolCalls = new ArrayList<>();
        this.webSearchResults = new ArrayList<>();
    }

    public String getTextContent() { return textContent; }
    public void setTextContent(String textContent) { this.textContent = textContent; }

    public List<ToolCallInfo> getToolCalls() { return toolCalls; }
    public void setToolCalls(List<ToolCallInfo> toolCalls) { this.toolCalls = toolCalls; }

    public List<WebSearchResult> getWebSearchResults() { return webSearchResults; }
    public void setWebSearchResults(List<WebSearchResult> webSearchResults) { this.webSearchResults = webSearchResults; }

    public int getInputTokens() { return inputTokens; }
    public void setInputTokens(int inputTokens) { this.inputTokens = inputTokens; }

    public int getOutputTokens() { return outputTokens; }
    public void setOutputTokens(int outputTokens) { this.outputTokens = outputTokens; }

    public int getCacheReadTokens() { return cacheReadTokens; }
    public void setCacheReadTokens(int cacheReadTokens) { this.cacheReadTokens = cacheReadTokens; }

    public int getCacheCreationTokens() { return cacheCreationTokens; }
    public void setCacheCreationTokens(int cacheCreationTokens) { this.cacheCreationTokens = cacheCreationTokens; }

    public int getCacheHitDenominator() { return cacheHitDenominator; }
    public void setCacheHitDenominator(int cacheHitDenominator) { this.cacheHitDenominator = cacheHitDenominator; }

    public Message getRawAssistantMessage() { return rawAssistantMessage; }
    public void setRawAssistantMessage(Message rawAssistantMessage) { this.rawAssistantMessage = rawAssistantMessage; }

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    public boolean hasWebSearchResults() {
        return webSearchResults != null && !webSearchResults.isEmpty();
    }
}
