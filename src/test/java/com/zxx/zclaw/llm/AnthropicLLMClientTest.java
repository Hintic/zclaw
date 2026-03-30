package com.zxx.zclaw.llm;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AnthropicLLMClientTest {

    private MockWebServer mockServer;
    private AnthropicLLMClient client;
    private AgentConfig config;
    private Gson gson;

    @BeforeEach
    void setUp() throws IOException {
        mockServer = new MockWebServer();
        mockServer.start();

        config = new AgentConfig();
        config.setBaseUrl(mockServer.url("/").toString());
        config.setApiKey("sk-ant-test-key");
        config.setModel("claude-sonnet-4-20250514");
        config.setWorkDir(Paths.get("/tmp"));
        config.setApiProvider("anthropic");
        config.setWebSearchEnabled(true);
        config.setWebSearchMaxUses(5);

        client = new AnthropicLLMClient(config);
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
                    "id": "msg_test1",
                    "type": "message",
                    "role": "assistant",
                    "content": [
                        {"type": "text", "text": "Hello! How can I help you?"}
                    ],
                    "model": "claude-sonnet-4-20250514",
                    "stop_reason": "end_turn",
                    "usage": {
                        "input_tokens": 25,
                        "output_tokens": 10
                    }
                }
                """;
        mockServer.enqueue(new MockResponse()
                .setBody(responseJson)
                .setHeader("Content-Type", "application/json"));

        ToolRegistry registry = new ToolRegistry();
        var messages = List.of(
                Message.system("Be helpful"),
                Message.user("Hi"));

        LLMResponse response = client.chat(messages, registry);

        assertEquals("Hello! How can I help you?", response.getTextContent());
        assertFalse(response.hasToolCalls());
        assertFalse(response.hasWebSearchResults());
        assertEquals(25, response.getInputTokens());
        assertEquals(10, response.getOutputTokens());

        // Verify request format
        RecordedRequest req = mockServer.takeRequest();
        assertEquals("POST", req.getMethod());
        assertTrue(req.getPath().endsWith("/v1/messages"));
        assertEquals("sk-ant-test-key", req.getHeader("x-api-key"));
        assertEquals("2023-06-01", req.getHeader("anthropic-version"));

        // Verify system prompt extracted to top level
        String body = req.getBody().readUtf8();
        JsonObject json = JsonParser.parseString(body).getAsJsonObject();
        assertTrue(json.has("system"));
        assertEquals("Be helpful", json.get("system").getAsString());
        // Messages should not include system
        var msgs = json.getAsJsonArray("messages");
        for (var m : msgs) {
            assertNotEquals("system", m.getAsJsonObject().get("role").getAsString());
        }
    }

    @Test
    void testChat_withToolUse() throws Exception {
        String responseJson = """
                {
                    "id": "msg_test2",
                    "type": "message",
                    "role": "assistant",
                    "content": [
                        {"type": "text", "text": "Let me read that file."},
                        {
                            "type": "tool_use",
                            "id": "toolu_abc",
                            "name": "read_file",
                            "input": {"file_path": "pom.xml"}
                        }
                    ],
                    "stop_reason": "tool_use",
                    "usage": {"input_tokens": 50, "output_tokens": 30}
                }
                """;
        mockServer.enqueue(new MockResponse()
                .setBody(responseJson)
                .setHeader("Content-Type", "application/json"));

        ToolRegistry registry = new ToolRegistry();
        var messages = List.of(Message.user("read pom.xml"));

        LLMResponse response = client.chat(messages, registry);

        assertEquals("Let me read that file.", response.getTextContent());
        assertTrue(response.hasToolCalls());
        assertEquals(1, response.getToolCalls().size());

        ToolCallInfo tc = response.getToolCalls().get(0);
        assertEquals("toolu_abc", tc.getId());
        assertEquals("read_file", tc.getName());
        assertTrue(tc.getArgumentsJson().contains("pom.xml"));
    }

    @Test
    void testChat_withWebSearch() throws Exception {
        String responseJson = """
                {
                    "id": "msg_test3",
                    "type": "message",
                    "role": "assistant",
                    "content": [
                        {
                            "type": "server_tool_use",
                            "id": "srvtoolu_abc",
                            "name": "web_search",
                            "input": {"query": "Java Agent framework 2025"}
                        },
                        {
                            "type": "web_search_tool_result",
                            "tool_use_id": "srvtoolu_abc",
                            "content": [
                                {
                                    "type": "web_search_result",
                                    "url": "https://github.com/langchain4j/langchain4j",
                                    "title": "langchain4j",
                                    "encrypted_content": "ENC...",
                                    "page_age": "3 days ago"
                                },
                                {
                                    "type": "web_search_result",
                                    "url": "https://github.com/spring-projects/spring-ai",
                                    "title": "Spring AI",
                                    "encrypted_content": "ENC...",
                                    "page_age": "1 week ago"
                                }
                            ]
                        },
                        {
                            "type": "text",
                            "text": "Based on my search, here are the top Java Agent frameworks..."
                        }
                    ],
                    "stop_reason": "end_turn",
                    "usage": {"input_tokens": 100, "output_tokens": 200}
                }
                """;
        mockServer.enqueue(new MockResponse()
                .setBody(responseJson)
                .setHeader("Content-Type", "application/json"));

        ToolRegistry registry = new ToolRegistry();
        var messages = List.of(Message.user("Search for Java Agent frameworks"));

        LLMResponse response = client.chat(messages, registry);

        // Text content
        assertEquals("Based on my search, here are the top Java Agent frameworks...",
                response.getTextContent());

        // No client-side tool calls
        assertFalse(response.hasToolCalls());

        // Web search results
        assertTrue(response.hasWebSearchResults());
        assertEquals(2, response.getWebSearchResults().size());

        WebSearchResult r1 = response.getWebSearchResults().get(0);
        assertEquals("langchain4j", r1.getTitle());
        assertEquals("https://github.com/langchain4j/langchain4j", r1.getUrl());
        assertEquals("Java Agent framework 2025", r1.getQuery());
        assertEquals("3 days ago", r1.getPageAge());

        WebSearchResult r2 = response.getWebSearchResults().get(1);
        assertEquals("Spring AI", r2.getTitle());
    }

    @Test
    void testChat_webSearchPlusToolUse() throws Exception {
        // Scenario: LLM searches the web AND calls a client-side tool in the same response
        String responseJson = """
                {
                    "id": "msg_test4",
                    "type": "message",
                    "role": "assistant",
                    "content": [
                        {
                            "type": "server_tool_use",
                            "id": "srvtoolu_s1",
                            "name": "web_search",
                            "input": {"query": "OkHttp latest version"}
                        },
                        {
                            "type": "web_search_tool_result",
                            "tool_use_id": "srvtoolu_s1",
                            "content": [
                                {
                                    "type": "web_search_result",
                                    "url": "https://square.github.io/okhttp/",
                                    "title": "OkHttp",
                                    "encrypted_content": "ENC...",
                                    "page_age": "1 day ago"
                                }
                            ]
                        },
                        {"type": "text", "text": "Let me also check your pom.xml."},
                        {
                            "type": "tool_use",
                            "id": "toolu_read1",
                            "name": "read_file",
                            "input": {"file_path": "pom.xml"}
                        }
                    ],
                    "stop_reason": "tool_use",
                    "usage": {"input_tokens": 80, "output_tokens": 60}
                }
                """;
        mockServer.enqueue(new MockResponse()
                .setBody(responseJson)
                .setHeader("Content-Type", "application/json"));

        ToolRegistry registry = new ToolRegistry();
        var messages = List.of(Message.user("Check OkHttp version in my pom.xml"));

        LLMResponse response = client.chat(messages, registry);

        // Has both web search and tool calls
        assertTrue(response.hasWebSearchResults());
        assertTrue(response.hasToolCalls());

        assertEquals(1, response.getWebSearchResults().size());
        assertEquals("OkHttp", response.getWebSearchResults().get(0).getTitle());

        assertEquals(1, response.getToolCalls().size());
        assertEquals("read_file", response.getToolCalls().get(0).getName());

        assertEquals("Let me also check your pom.xml.", response.getTextContent());
    }

    @Test
    void testChat_httpError() {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(401)
                .setBody("{\"error\":{\"type\":\"authentication_error\",\"message\":\"invalid api key\"}}")
                .setHeader("Content-Type", "application/json"));

        ToolRegistry registry = new ToolRegistry();
        var messages = List.of(Message.user("Hi"));

        IOException ex = assertThrows(IOException.class, () -> client.chat(messages, registry));
        assertTrue(ex.getMessage().contains("401"));
        assertTrue(ex.getMessage().contains("authentication_error"));
    }

    @Test
    void testChat_webSearchToolDeclaration() throws Exception {
        // Verify that the web search tool is included in the request
        String responseJson = """
                {
                    "id": "msg_test5",
                    "type": "message",
                    "role": "assistant",
                    "content": [{"type": "text", "text": "OK"}],
                    "stop_reason": "end_turn",
                    "usage": {"input_tokens": 10, "output_tokens": 2}
                }
                """;
        mockServer.enqueue(new MockResponse()
                .setBody(responseJson)
                .setHeader("Content-Type", "application/json"));

        ToolRegistry registry = new ToolRegistry();
        var messages = List.of(Message.user("Hi"));

        client.chat(messages, registry);

        RecordedRequest req = mockServer.takeRequest();
        String body = req.getBody().readUtf8();
        JsonObject json = JsonParser.parseString(body).getAsJsonObject();

        // Should have tools array with web_search tool
        assertTrue(json.has("tools"));
        var tools = json.getAsJsonArray("tools");
        assertTrue(tools.size() >= 1);

        // First tool should be web_search
        var firstTool = tools.get(0).getAsJsonObject();
        assertEquals("web_search_20250305", firstTool.get("type").getAsString());
        assertEquals("web_search", firstTool.get("name").getAsString());
        assertEquals(5, firstTool.get("max_uses").getAsInt());
    }

    @Test
    void testChat_webSearchDisabled() throws Exception {
        // Disable web search
        config.setWebSearchEnabled(false);
        client.shutdown();
        client = new AnthropicLLMClient(config);

        String responseJson = """
                {
                    "id": "msg_test6",
                    "type": "message",
                    "role": "assistant",
                    "content": [{"type": "text", "text": "OK"}],
                    "stop_reason": "end_turn",
                    "usage": {"input_tokens": 10, "output_tokens": 2}
                }
                """;
        mockServer.enqueue(new MockResponse()
                .setBody(responseJson)
                .setHeader("Content-Type", "application/json"));

        ToolRegistry registry = new ToolRegistry();
        var messages = List.of(Message.user("Hi"));

        client.chat(messages, registry);

        RecordedRequest req = mockServer.takeRequest();
        String body = req.getBody().readUtf8();
        JsonObject json = JsonParser.parseString(body).getAsJsonObject();

        // Tools should be null or not contain web_search
        if (json.has("tools") && !json.get("tools").isJsonNull()) {
            var tools = json.getAsJsonArray("tools");
            for (var t : tools) {
                var obj = t.getAsJsonObject();
                if (obj.has("type")) {
                    assertNotEquals("web_search_20250305", obj.get("type").getAsString());
                }
            }
        }
    }

    @Test
    void testCreateToolResultMessage() {
        Message msg = client.createToolResultMessage("toolu_123", "file contents here");
        assertEquals("user", msg.getRole());
        assertTrue(msg.getContent() instanceof List);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) msg.getContent();
        assertEquals(1, content.size());
        assertEquals("tool_result", content.get(0).get("type"));
        assertEquals("toolu_123", content.get(0).get("tool_use_id"));
        assertEquals("file contents here", content.get(0).get("content"));
    }

    @Test
    void testChat_multipleSearches() throws Exception {
        String responseJson = """
                {
                    "id": "msg_test7",
                    "type": "message",
                    "role": "assistant",
                    "content": [
                        {
                            "type": "server_tool_use",
                            "id": "srvtoolu_1",
                            "name": "web_search",
                            "input": {"query": "first query"}
                        },
                        {
                            "type": "web_search_tool_result",
                            "tool_use_id": "srvtoolu_1",
                            "content": [
                                {"type": "web_search_result", "url": "https://a.com", "title": "Result A", "page_age": "1d"}
                            ]
                        },
                        {
                            "type": "server_tool_use",
                            "id": "srvtoolu_2",
                            "name": "web_search",
                            "input": {"query": "second query"}
                        },
                        {
                            "type": "web_search_tool_result",
                            "tool_use_id": "srvtoolu_2",
                            "content": [
                                {"type": "web_search_result", "url": "https://b.com", "title": "Result B", "page_age": "2d"}
                            ]
                        },
                        {"type": "text", "text": "Summary of both searches."}
                    ],
                    "stop_reason": "end_turn",
                    "usage": {"input_tokens": 150, "output_tokens": 100}
                }
                """;
        mockServer.enqueue(new MockResponse()
                .setBody(responseJson)
                .setHeader("Content-Type", "application/json"));

        ToolRegistry registry = new ToolRegistry();
        var messages = List.of(Message.user("Search for two things"));

        LLMResponse response = client.chat(messages, registry);

        assertEquals("Summary of both searches.", response.getTextContent());
        assertEquals(2, response.getWebSearchResults().size());
        assertEquals("first query", response.getWebSearchResults().get(0).getQuery());
        assertEquals("second query", response.getWebSearchResults().get(1).getQuery());
    }

    @Test
    void testChat_webSearchNoResults() throws Exception {
        String responseJson = """
                {
                    "id": "msg_test8",
                    "type": "message",
                    "role": "assistant",
                    "content": [
                        {
                            "type": "server_tool_use",
                            "id": "srvtoolu_empty",
                            "name": "web_search",
                            "input": {"query": "very obscure query xyz123"}
                        },
                        {
                            "type": "web_search_tool_result",
                            "tool_use_id": "srvtoolu_empty",
                            "content": []
                        },
                        {"type": "text", "text": "I couldn't find relevant results."}
                    ],
                    "stop_reason": "end_turn",
                    "usage": {"input_tokens": 30, "output_tokens": 15}
                }
                """;
        mockServer.enqueue(new MockResponse()
                .setBody(responseJson)
                .setHeader("Content-Type", "application/json"));

        ToolRegistry registry = new ToolRegistry();
        var messages = List.of(Message.user("search for something obscure"));

        LLMResponse response = client.chat(messages, registry);

        assertEquals("I couldn't find relevant results.", response.getTextContent());
        assertFalse(response.hasWebSearchResults());
    }

    @Test
    void testChat_preservesConversationHistory() throws Exception {
        // Verify that Anthropic content blocks are preserved in raw assistant message
        String responseJson = """
                {
                    "id": "msg_test9",
                    "type": "message",
                    "role": "assistant",
                    "content": [
                        {"type": "server_tool_use", "id": "srv1", "name": "web_search", "input": {"query": "test"}},
                        {"type": "web_search_tool_result", "tool_use_id": "srv1", "content": []},
                        {"type": "text", "text": "Result here"}
                    ],
                    "stop_reason": "end_turn",
                    "usage": {"input_tokens": 20, "output_tokens": 10}
                }
                """;
        mockServer.enqueue(new MockResponse()
                .setBody(responseJson)
                .setHeader("Content-Type", "application/json"));

        ToolRegistry registry = new ToolRegistry();
        var messages = List.of(Message.user("test"));

        LLMResponse response = client.chat(messages, registry);

        Message raw = response.getRawAssistantMessage();
        assertNotNull(raw);
        assertEquals("assistant", raw.getRole());
        assertNotNull(raw.getAnthropicContent());
        assertEquals(3, raw.getAnthropicContent().size());
    }
}
