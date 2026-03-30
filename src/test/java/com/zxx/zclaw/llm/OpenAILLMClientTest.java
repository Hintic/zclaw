package com.zxx.zclaw.llm;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.zxx.zclaw.config.AgentConfig;
import com.zxx.zclaw.llm.model.*;
import com.zxx.zclaw.tool.ToolRegistry;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OpenAILLMClientTest {

    private MockWebServer mockServer;
    private OpenAILLMClient client;
    private AgentConfig config;
    private Gson gson;

    @BeforeEach
    void setUp() throws IOException {
        mockServer = new MockWebServer();
        mockServer.start();

        config = new AgentConfig();
        config.setBaseUrl(mockServer.url("/").toString());
        config.setApiKey("test-key");
        config.setModel("test-model");
        config.setWorkDir(Paths.get("/tmp"));

        client = new OpenAILLMClient(config);
        gson = new GsonBuilder().disableHtmlEscaping().create();
    }

    @AfterEach
    void tearDown() throws IOException {
        client.shutdown();
        mockServer.shutdown();
    }

    @Test
    void testChat_textResponse() throws Exception {
        String responseJson = """
                {
                    "id": "chatcmpl-123",
                    "choices": [{
                        "index": 0,
                        "message": {
                            "role": "assistant",
                            "content": "Hello! I'm here to help."
                        },
                        "finish_reason": "stop"
                    }],
                    "usage": {
                        "prompt_tokens": 10,
                        "completion_tokens": 8,
                        "total_tokens": 18
                    }
                }
                """;
        mockServer.enqueue(new MockResponse()
                .setBody(responseJson)
                .setHeader("Content-Type", "application/json"));

        ToolRegistry registry = new ToolRegistry();
        var messages = java.util.List.of(
                Message.system("Be helpful"),
                Message.user("Hi"));

        LLMResponse response = client.chat(messages, registry);

        assertEquals("Hello! I'm here to help.", response.getTextContent());
        assertFalse(response.hasToolCalls());
        assertFalse(response.hasWebSearchResults());
        assertEquals(10, response.getInputTokens());
        assertEquals(8, response.getOutputTokens());
        assertNotNull(response.getRawAssistantMessage());

        // Verify request
        RecordedRequest req = mockServer.takeRequest();
        assertEquals("POST", req.getMethod());
        assertTrue(req.getPath().endsWith("/v1/chat/completions"));
        assertTrue(req.getHeader("Authorization").contains("Bearer test-key"));
    }

    @Test
    void testChat_withToolCalls() throws Exception {
        String responseJson = """
                {
                    "id": "chatcmpl-456",
                    "choices": [{
                        "index": 0,
                        "message": {
                            "role": "assistant",
                            "content": null,
                            "tool_calls": [{
                                "id": "call_abc",
                                "type": "function",
                                "function": {
                                    "name": "read_file",
                                    "arguments": "{\\"file_path\\": \\"test.txt\\"}"
                                }
                            }]
                        },
                        "finish_reason": "tool_calls"
                    }],
                    "usage": {
                        "prompt_tokens": 20,
                        "completion_tokens": 15,
                        "total_tokens": 35
                    }
                }
                """;
        mockServer.enqueue(new MockResponse()
                .setBody(responseJson)
                .setHeader("Content-Type", "application/json"));

        ToolRegistry registry = new ToolRegistry();
        var messages = java.util.List.of(Message.user("read test.txt"));

        LLMResponse response = client.chat(messages, registry);

        assertNull(response.getTextContent());
        assertTrue(response.hasToolCalls());
        assertEquals(1, response.getToolCalls().size());

        ToolCallInfo tc = response.getToolCalls().get(0);
        assertEquals("call_abc", tc.getId());
        assertEquals("read_file", tc.getName());
        assertTrue(tc.getArgumentsJson().contains("test.txt"));
    }

    @Test
    void testChat_httpError() {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(429)
                .setBody("{\"error\":\"rate limit exceeded\"}")
                .setHeader("Content-Type", "application/json"));

        ToolRegistry registry = new ToolRegistry();
        var messages = java.util.List.of(Message.user("Hi"));

        IOException ex = assertThrows(IOException.class, () -> client.chat(messages, registry));
        assertTrue(ex.getMessage().contains("429"));
        assertTrue(ex.getMessage().contains("rate limit"));
    }

    @Test
    void testChat_malformedResponse() {
        mockServer.enqueue(new MockResponse()
                .setBody("not json at all")
                .setHeader("Content-Type", "application/json"));

        ToolRegistry registry = new ToolRegistry();
        var messages = java.util.List.of(Message.user("Hi"));

        assertThrows(Exception.class, () -> client.chat(messages, registry));
    }

    @Test
    void testCreateToolResultMessage() {
        Message msg = client.createToolResultMessage("call_123", "result data");
        assertEquals("tool", msg.getRole());
        assertEquals("call_123", msg.getToolCallId());
        assertEquals("result data", msg.getContentAsString());
    }
}
