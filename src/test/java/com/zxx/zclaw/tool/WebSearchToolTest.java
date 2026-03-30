package com.zxx.zclaw.tool;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class WebSearchToolTest {

    private MockWebServer mockServer;
    private WebSearchTool tool;

    @BeforeEach
    void setUp() throws IOException {
        mockServer = new MockWebServer();
        mockServer.start();
        String baseUrl = mockServer.url("/").toString();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        tool = new WebSearchTool(baseUrl, "test-api-key", "gemini-2.5-flash");
    }

    @AfterEach
    void tearDown() throws IOException {
        mockServer.shutdown();
        tool.shutdown();
    }

    @Test
    void testName() {
        assertEquals("web_search", tool.name());
    }

    @Test
    void testDescription() {
        assertNotNull(tool.description());
        assertFalse(tool.description().isEmpty());
        assertTrue(tool.description().contains("Search the web"));
    }

    @Test
    void testParameters() {
        Map<String, Object> params = tool.parameters();
        assertEquals("object", params.get("type"));
        assertNotNull(params.get("properties"));
        assertNotNull(params.get("required"));
    }

    @Test
    void testExecute_success() throws Exception {
        String responseJson = """
                {
                  "id": "chatcmpl-123",
                  "object": "chat.completion",
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "content": "Java has several popular agent frameworks including LangChain4j and Spring AI."
                      },
                      "finish_reason": "stop",
                      "groundingMetadata": {
                        "webSearchQueries": ["Java agent framework 2025"],
                        "groundingChunks": [
                          {"web": {"uri": "https://github.com/langchain4j", "title": "LangChain4j"}},
                          {"web": {"uri": "https://spring.io/projects/spring-ai", "title": "Spring AI"}}
                        ]
                      }
                    }
                  ]
                }
                """;

        mockServer.enqueue(new MockResponse()
                .setBody(responseJson)
                .setHeader("Content-Type", "application/json"));

        String result = tool.execute(Map.of("query", "Java agent framework"));

        assertTrue(result.contains("Web Search Results for:"));
        assertTrue(result.contains("Java has several popular agent frameworks"));
        assertTrue(result.contains("LangChain4j"));
        assertTrue(result.contains("https://github.com/langchain4j"));
        assertTrue(result.contains("Spring AI"));
        assertTrue(result.contains("https://spring.io/projects/spring-ai"));

        // Verify request was correct
        RecordedRequest request = mockServer.takeRequest(1, TimeUnit.SECONDS);
        assertNotNull(request);
        assertTrue(request.getPath().contains("/v1/chat/completions"));
        assertEquals("Bearer test-api-key", request.getHeader("Authorization"));
        String body = request.getBody().readUtf8();
        assertTrue(body.contains("googleSearch"));
        assertTrue(body.contains("Java agent framework"));
        assertTrue(body.contains("gemini-2.5-flash"));
    }

    @Test
    void testExecute_noGroundingMetadata() {
        String responseJson = """
                {
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "content": "Some general answer without grounding."
                      },
                      "finish_reason": "stop"
                    }
                  ]
                }
                """;

        mockServer.enqueue(new MockResponse()
                .setBody(responseJson)
                .setHeader("Content-Type", "application/json"));

        String result = tool.execute(Map.of("query", "test query"));

        assertTrue(result.contains("Some general answer without grounding."));
        assertFalse(result.contains("### Sources"));
    }

    @Test
    void testExecute_noGroundingChunks() {
        String responseJson = """
                {
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "content": "Answer with metadata but no chunks."
                      },
                      "finish_reason": "stop",
                      "groundingMetadata": {
                        "webSearchQueries": ["test query"],
                        "groundingChunks": []
                      }
                    }
                  ]
                }
                """;

        mockServer.enqueue(new MockResponse()
                .setBody(responseJson)
                .setHeader("Content-Type", "application/json"));

        String result = tool.execute(Map.of("query", "test query"));

        assertTrue(result.contains("Answer with metadata but no chunks."));
        assertFalse(result.contains("### Sources"));
    }

    @Test
    void testExecute_emptyQuery() {
        String result = tool.execute(Map.of("query", ""));
        assertTrue(result.contains("Error: search query is empty"));
    }

    @Test
    void testExecute_nullQuery() {
        String result = tool.execute(Map.of());
        assertTrue(result.contains("Error: search query is empty"));
    }

    @Test
    void testExecute_httpError_429() {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(429)
                .setBody("{\"error\":{\"code\":429,\"message\":\"Resource has been exhausted\"}}"));

        String result = tool.execute(Map.of("query", "test"));

        assertTrue(result.contains("Error: web search API error 429"));
        assertTrue(result.contains("Resource has been exhausted"));
    }

    @Test
    void testExecute_httpError_401() {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(401)
                .setBody("{\"error\":{\"code\":401,\"message\":\"API key not valid\"}}"));

        String result = tool.execute(Map.of("query", "test"));

        assertTrue(result.contains("Error: web search API error 401"));
        assertTrue(result.contains("API key not valid"));
    }

    @Test
    void testExecute_httpError_500() {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(500)
                .setBody("{\"error\":{\"code\":500,\"message\":\"Internal server error\"}}"));

        String result = tool.execute(Map.of("query", "test"));

        assertTrue(result.contains("Error: web search API error 500"));
    }

    @Test
    void testExecute_timeout() throws Exception {
        MockWebServer timeoutServer = new MockWebServer();
        timeoutServer.start();

        OkHttpClient shortTimeoutClient = new OkHttpClient.Builder()
                .connectTimeout(100, TimeUnit.MILLISECONDS)
                .readTimeout(100, TimeUnit.MILLISECONDS)
                .build();
        String baseUrl = timeoutServer.url("").toString();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        WebSearchTool shortTimeoutTool = new WebSearchTool(
                shortTimeoutClient, baseUrl, "test-key", "gemini-2.5-flash");

        timeoutServer.enqueue(new MockResponse()
                .setBody("{}")
                .setBodyDelay(5, TimeUnit.SECONDS));

        String result = shortTimeoutTool.execute(Map.of("query", "test timeout"));

        assertTrue(result.startsWith("Error:"));
        shortTimeoutTool.shutdown();
        timeoutServer.close();
    }

    @Test
    void testExecute_malformedJson() {
        mockServer.enqueue(new MockResponse()
                .setBody("this is not json")
                .setHeader("Content-Type", "application/json"));

        String result = tool.execute(Map.of("query", "test"));

        assertTrue(result.contains("Error: Failed to parse search response"));
    }

    @Test
    void testExecute_multipleChunks() {
        String responseJson = """
                {
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "content": "Multiple sources found."
                      },
                      "finish_reason": "stop",
                      "groundingMetadata": {
                        "webSearchQueries": ["test"],
                        "groundingChunks": [
                          {"web": {"uri": "https://a.com", "title": "Source A"}},
                          {"web": {"uri": "https://b.com", "title": "Source B"}},
                          {"web": {"uri": "https://c.com", "title": "Source C"}}
                        ]
                      }
                    }
                  ]
                }
                """;

        mockServer.enqueue(new MockResponse()
                .setBody(responseJson)
                .setHeader("Content-Type", "application/json"));

        String result = tool.execute(Map.of("query", "test"));

        assertTrue(result.contains("1. [Source A] https://a.com"));
        assertTrue(result.contains("2. [Source B] https://b.com"));
        assertTrue(result.contains("3. [Source C] https://c.com"));
    }

    @Test
    void testExecute_multipleQueries() {
        String responseJson = """
                {
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "content": "Results from multiple queries."
                      },
                      "finish_reason": "stop",
                      "groundingMetadata": {
                        "webSearchQueries": ["query one", "query two", "query three"],
                        "groundingChunks": [
                          {"web": {"uri": "https://example.com", "title": "Example"}}
                        ]
                      }
                    }
                  ]
                }
                """;

        mockServer.enqueue(new MockResponse()
                .setBody(responseJson)
                .setHeader("Content-Type", "application/json"));

        String result = tool.execute(Map.of("query", "multi query test"));

        assertTrue(result.contains("Results from multiple queries."));
        assertTrue(result.contains("Example"));
    }

    @Test
    void testBuildUrl() {
        assertEquals(mockServer.url("").toString().replaceAll("/$", "")
                + "/v1/chat/completions", tool.buildUrl());
    }

    @Test
    void testBuildUrl_trailingSlash() {
        WebSearchTool toolWithSlash = new WebSearchTool("https://gateway.com/llm/", "key", "gemini-2.0-flash");
        assertEquals("https://gateway.com/llm/v1/chat/completions",
                toolWithSlash.buildUrl());
    }

    @Test
    void testBuildRequestBody() {
        String body = tool.buildRequestBody("test query");
        assertTrue(body.contains("googleSearch"));
        assertTrue(body.contains("Search for: test query"));
        assertTrue(body.contains("gemini-2.5-flash"));
        assertTrue(body.contains("\"model\""));
        assertTrue(body.contains("\"messages\""));
    }

    @Test
    void testExecute_emptyChoices() {
        mockServer.enqueue(new MockResponse()
                .setBody("{\"choices\": []}")
                .setHeader("Content-Type", "application/json"));

        String result = tool.execute(Map.of("query", "test"));

        assertTrue(result.contains("Error: web search returned no results"));
    }

    @Test
    void testExecute_nullContent() {
        String responseJson = """
                {
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "content": null
                      },
                      "finish_reason": "stop"
                    }
                  ]
                }
                """;

        mockServer.enqueue(new MockResponse()
                .setBody(responseJson)
                .setHeader("Content-Type", "application/json"));

        String result = tool.execute(Map.of("query", "test"));

        assertTrue(result.contains("Error: web search returned no results"));
    }
}
