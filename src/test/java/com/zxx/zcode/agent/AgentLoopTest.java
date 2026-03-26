package com.zxx.zcode.agent;

import com.zxx.zcode.config.AgentConfig;
import com.zxx.zcode.llm.LLMClient;
import com.zxx.zcode.llm.model.*;
import com.zxx.zcode.tool.ToolRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AgentLoopTest {

    private AgentConfig config;
    private ToolRegistry toolRegistry;
    private ByteArrayOutputStream outputStream;
    private PrintStream out;

    @BeforeEach
    void setUp() {
        config = new AgentConfig();
        config.setWorkDir(Paths.get("/tmp"));
        config.setApiProvider("anthropic");
        config.setWebSearchEnabled(true);
        config.setMemoryEnabled(false);
        toolRegistry = new ToolRegistry();
        outputStream = new ByteArrayOutputStream();
        out = new PrintStream(outputStream);
    }

    @Test
    void testProcessInput_textResponse() throws Exception {
        LLMClient mockClient = new LLMClient() {
            @Override
            public LLMResponse chat(List<Message> messages, ToolRegistry registry) {
                LLMResponse resp = new LLMResponse();
                resp.setTextContent("Hello user!");
                resp.setRawAssistantMessage(Message.assistant("Hello user!", null));
                resp.setInputTokens(10);
                resp.setOutputTokens(5);
                return resp;
            }

            @Override
            public void chatStream(List<Message> messages, ToolRegistry registry, StreamingConsumer consumer) throws IOException {
                consumer.onTextDelta("Hello user!");
                consumer.onComplete(10, 5, Message.assistant("Hello user!", null));
            }

            @Override
            public Message createToolResultMessage(String toolCallId, String content) {
                return Message.toolResult(toolCallId, content);
            }

            @Override
            public void shutdown() {}
        };

        AgentLoop loop = new AgentLoop(config, mockClient, toolRegistry, out);
        String result = loop.processInput("Hi");

        assertEquals("Hello user!", result);
        String output = outputStream.toString();
        assertTrue(output.contains("tokens:"));
    }

    @Test
    void testProcessInput_withWebSearchResults() throws Exception {
        LLMClient mockClient = new LLMClient() {
            @Override
            public LLMResponse chat(List<Message> messages, ToolRegistry registry) {
                LLMResponse resp = new LLMResponse();
                resp.setTextContent("Based on search results...");
                resp.setRawAssistantMessage(Message.assistant("Based on search results...", null));
                resp.setWebSearchResults(List.of(
                        new WebSearchResult("test query", "https://example.com", "Example", "1d ago")
                ));
                resp.setInputTokens(50);
                resp.setOutputTokens(30);
                return resp;
            }

            @Override
            public void chatStream(List<Message> messages, ToolRegistry registry, StreamingConsumer consumer) throws IOException {
                consumer.onTextDelta("Based on search results...\n");
                consumer.onTextDelta("Example: https://example.com");
                consumer.onComplete(50, 30, Message.assistant("Based on search results...\nExample: https://example.com", null));
            }

            @Override
            public Message createToolResultMessage(String toolCallId, String content) {
                return Message.toolResult(toolCallId, content);
            }

            @Override
            public void shutdown() {}
        };

        AgentLoop loop = new AgentLoop(config, mockClient, toolRegistry, out);
        String result = loop.processInput("search something");

        assertEquals("Based on search results...", result);
        String output = outputStream.toString();
        assertTrue(output.contains("Example"));
        assertTrue(output.contains("https://example.com"));
    }

    @Test
    void testProcessInput_withToolCall() throws Exception {
        // Mock client that first returns a tool call, then a text response
        LLMClient mockClient = new LLMClient() {
            private int callCount = 0;

            @Override
            public LLMResponse chat(List<Message> messages, ToolRegistry registry) {
                callCount++;
                LLMResponse resp = new LLMResponse();
                if (callCount == 1) {
                    // First call: tool call
                    resp.setToolCalls(List.of(new ToolCallInfo("call_1", "unknown_tool", "{}")));
                    resp.setRawAssistantMessage(Message.assistant(null, null));
                } else {
                    // Second call: text response
                    resp.setTextContent("Done!");
                    resp.setRawAssistantMessage(Message.assistant("Done!", null));
                }
                return resp;
            }

            @Override
            public void chatStream(List<Message> messages, ToolRegistry registry, StreamingConsumer consumer) throws IOException {
                callCount++;
                if (callCount == 1) {
                    // First call: tool call
                    consumer.onToolCall("call_1", "unknown_tool", "{}");
                    consumer.onComplete(5, 1, Message.assistant(null, null));
                } else {
                    // Second call: text response
                    consumer.onTextDelta("Done!");
                    consumer.onComplete(10, 5, Message.assistant("Done!", null));
                }
            }

            @Override
            public Message createToolResultMessage(String toolCallId, String content) {
                return Message.toolResult(toolCallId, content);
            }

            @Override
            public void shutdown() {}
        };

        AgentLoop loop = new AgentLoop(config, mockClient, toolRegistry, out);
        String result = loop.processInput("do something");

        assertEquals("Done!", result);
        String output = outputStream.toString();
        assertTrue(output.contains("unknown_tool"));
    }

    @Test
    void testClearHistory() {
        LLMClient mockClient = new LLMClient() {
            @Override
            public LLMResponse chat(List<Message> messages, ToolRegistry registry) {
                return new LLMResponse();
            }

            @Override
            public void chatStream(List<Message> messages, ToolRegistry registry, StreamingConsumer consumer) throws IOException {
                consumer.onComplete(0, 0, Message.assistant("", null));
            }

            @Override
            public Message createToolResultMessage(String toolCallId, String content) {
                return Message.toolResult(toolCallId, content);
            }

            @Override
            public void shutdown() {}
        };

        AgentLoop loop = new AgentLoop(config, mockClient, toolRegistry, out);
        loop.clearHistory();
        assertEquals(0, loop.messageCount());
    }

    @Test
    void testProcessIsolated_noToolsNoHistory() throws Exception {
        // Use separate mocks to verify behavior
        LLMClient mockClient = new LLMClient() {
            @Override
            public LLMResponse chat(List<Message> messages, ToolRegistry registry) {
                // Isolated: registry is null
                assertNull(registry);
                // Isolated: system prompt + user message (no conversation history)
                assertEquals(2, messages.size());
                assertEquals("system", messages.get(0).getRole());
                assertEquals("user", messages.get(1).getRole());
                String userContent = String.valueOf(messages.get(1).getContent());
                assertTrue(userContent.contains("2+2"));

                LLMResponse resp = new LLMResponse();
                resp.setTextContent("4");
                resp.setRawAssistantMessage(Message.assistant("4", null));
                resp.setInputTokens(5);
                resp.setOutputTokens(1);
                return resp;
            }

            @Override
            public void chatStream(List<Message> messages, ToolRegistry registry, StreamingConsumer consumer) throws IOException {
                assertNull(registry);
                assertEquals(2, messages.size());
                String content = String.valueOf(messages.get(1).getContent());
                assertTrue(content.contains("2+2"));
                consumer.onTextDelta("4");
                consumer.onComplete(5, 1, Message.assistant("4", null));
            }

            @Override
            public Message createToolResultMessage(String toolCallId, String content) {
                return Message.toolResult(toolCallId, content);
            }

            @Override
            public void shutdown() {}
        };

        AgentLoop loop = new AgentLoop(config, mockClient, toolRegistry, out);
        String result = loop.processIsolated("What is 2+2?");
        assertEquals("4", result);
    }
}
