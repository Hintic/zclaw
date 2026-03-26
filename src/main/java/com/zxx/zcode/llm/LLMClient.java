package com.zxx.zcode.llm;

import com.zxx.zcode.llm.model.LLMResponse;
import com.zxx.zcode.llm.model.Message;
import com.zxx.zcode.tool.ToolRegistry;

import java.io.IOException;
import java.util.List;

/**
 * Abstraction for LLM API clients (OpenAI-compatible and Anthropic Messages API).
 */
public interface LLMClient {

    /**
     * Send a chat request to the LLM.
     *
     * @param messages     conversation history
     * @param toolRegistry tool registry for tool definitions
     * @return unified response
     */
    LLMResponse chat(List<Message> messages, ToolRegistry toolRegistry) throws IOException;

    /**
     * Callback interface for streaming responses.
     */
    interface StreamingConsumer {
        /** Called for each text delta. Return false to cancel streaming. */
        boolean onTextDelta(String delta);
        /** Called when a tool call is detected. */
        void onToolCall(String id, String name, String argumentsJson);
        /** Called when the stream finishes with the final usage stats. */
        void onComplete(int inputTokens, int outputTokens, Message rawAssistantMessage);
        /** Called on stream error. */
        void onError(Throwable t);
    }

    /**
     * Stream a chat response from the LLM using SSE.
     * Each text delta is delivered via the consumer callback.
     * Thinking blocks are automatically detected and skipped.
     *
     * @param messages     conversation history
     * @param toolRegistry tool registry for tool definitions
     * @param consumer     callback for streaming events
     */
    void chatStream(List<Message> messages, ToolRegistry toolRegistry, StreamingConsumer consumer) throws IOException;

    /**
     * Create a tool result message in the format expected by this client's API.
     *
     * @param toolCallId the tool call ID to respond to
     * @param content    the tool execution result
     * @return a Message that can be added to conversation history
     */
    Message createToolResultMessage(String toolCallId, String content);

    /**
     * Release resources.
     */
    void shutdown();
}
