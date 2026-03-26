package com.zxx.zcode.tool;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Web search tool that uses Gemini Google Search grounding via OpenAI-compatible chat/completions API.
 * Reuses the same base_url and api_key as the main LLM client (via Gateway).
 */
public class WebSearchTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(WebSearchTool.class);
    private static final MediaType JSON_TYPE = MediaType.get("application/json; charset=utf-8");
    private static final int TIMEOUT_SECONDS = 30;

    private final OkHttpClient httpClient;
    private final Gson gson;
    private final String baseUrl;
    private final String apiKey;
    private final String searchModel;

    public WebSearchTool(String baseUrl, String apiKey, String searchModel) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.searchModel = searchModel;
        this.gson = new Gson();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build();
    }

    // Visible for testing
    WebSearchTool(OkHttpClient httpClient, String baseUrl, String apiKey, String searchModel) {
        this.httpClient = httpClient;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.searchModel = searchModel;
        this.gson = new Gson();
    }

    @Override
    public String name() {
        return "web_search";
    }

    @Override
    public String description() {
        return "Search the web for real-time information using Google Search. "
                + "Use this tool when you need current information, recent events, "
                + "documentation, GitHub projects, or any information that may be "
                + "more recent than your training data.";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "query", Map.of(
                                "type", "string",
                                "description", "The search query"
                        )
                ),
                "required", List.of("query")
        );
    }

    @Override
    public String execute(Map<String, Object> args) {
        String query = (String) args.get("query");
        if (query == null || query.isBlank()) {
            return "Error: search query is empty";
        }

        log.info("WebSearchTool executing: query=\"{}\"", query);

        String url = buildUrl();
        String requestBody = buildRequestBody(query);

        log.info("POST {}", url);

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(requestBody, JSON_TYPE))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";

            if (!response.isSuccessful()) {
                log.error("WebSearchTool error: HTTP {} {}\n        URL: {}\n        Response body: {}",
                        response.code(), response.message(), url, responseBody);
                return "Error: web search API error " + response.code() + ": " + responseBody;
            }

            log.info("{} OK (search completed)", response.code());
            return parseAndFormat(query, responseBody);

        } catch (IOException e) {
            if (e.getMessage() != null && (e.getMessage().contains("timeout") || e.getMessage().contains("Timeout"))) {
                log.error("WebSearchTool timeout: {}", e.getMessage(), e);
                return "Error: web search request timed out after " + TIMEOUT_SECONDS + "s";
            }
            log.error("WebSearchTool error: {}", e.getMessage(), e);
            return "Error: WebSearchTool request failed: " + e.getMessage();
        }
    }

    String buildUrl() {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return base + "/v1/chat/completions";
    }

    String buildRequestBody(String query) {
        Map<String, Object> body = Map.of(
                "model", searchModel,
                "messages", List.of(
                        Map.of("role", "user", "content", "Search for: " + query)
                ),
                "tools", List.of(
                        Map.of("googleSearch", Map.of())
                )
        );
        return gson.toJson(body);
    }

    String parseAndFormat(String query, String responseBody) {
        try {
            JsonObject root = gson.fromJson(responseBody, JsonObject.class);

            // Extract content text from OpenAI format: choices[0].message.content
            String contentText = extractContent(root);
            if (contentText == null) {
                log.warn("Response has no content text");
                return "Error: web search returned no results for query: " + query;
            }

            // Build formatted result
            StringBuilder result = new StringBuilder();
            result.append("## Web Search Results for: \"").append(query).append("\"\n\n");

            result.append("### Summary\n");
            result.append(contentText).append("\n\n");

            // Try to extract grounding metadata (Gateway may or may not pass through)
            List<Source> sources = extractSources(root);
            if (!sources.isEmpty()) {
                result.append("### Sources\n");
                for (int i = 0; i < sources.size(); i++) {
                    Source src = sources.get(i);
                    result.append(i + 1).append(". [").append(src.title).append("] ").append(src.uri).append("\n");
                    log.info("  source: {} - {}", src.title, src.uri);
                }
                log.info("Sources: {} results", sources.size());
            }

            result.append("\n---\n");
            result.append("Use the above search results to answer the user's question. Cite sources when referencing specific information.");

            String formatted = result.toString();
            log.info("Summary: {} chars, total result: {} chars", contentText.length(), formatted.length());
            return formatted;

        } catch (Exception e) {
            log.error("Failed to parse search response: {}", e.getMessage(), e);
            return "Error: Failed to parse search response: " + e.getMessage();
        }
    }

    private String extractContent(JsonObject root) {
        // OpenAI format: choices[0].message.content
        JsonArray choices = root.getAsJsonArray("choices");
        if (choices != null && !choices.isEmpty()) {
            JsonObject choice = choices.get(0).getAsJsonObject();
            JsonObject message = choice.getAsJsonObject("message");
            if (message != null && message.has("content") && !message.get("content").isJsonNull()) {
                return message.get("content").getAsString();
            }
        }
        return null;
    }

    private List<Source> extractSources(JsonObject root) {
        List<Source> sources = new ArrayList<>();

        // Try choices[0].groundingMetadata.groundingChunks (Gateway may pass through)
        JsonArray choices = root.getAsJsonArray("choices");
        if (choices != null && !choices.isEmpty()) {
            JsonObject choice = choices.get(0).getAsJsonObject();
            JsonObject metadata = choice.getAsJsonObject("groundingMetadata");
            if (metadata != null) {
                extractFromGroundingChunks(metadata, sources);
            }
        }

        return sources;
    }

    private void extractFromGroundingChunks(JsonObject metadata, List<Source> sources) {
        JsonArray chunks = metadata.getAsJsonArray("groundingChunks");
        if (chunks == null) return;

        // Log actual search queries if available
        JsonArray queries = metadata.getAsJsonArray("webSearchQueries");
        if (queries != null && !queries.isEmpty()) {
            log.info("Actual queries: {}", queries);
        }

        for (JsonElement chunkElem : chunks) {
            JsonObject chunk = chunkElem.getAsJsonObject();
            JsonObject web = chunk.getAsJsonObject("web");
            if (web != null) {
                String title = web.has("title") ? web.get("title").getAsString() : "Unknown";
                String uri = web.has("uri") ? web.get("uri").getAsString() : "";
                sources.add(new Source(title, uri));
            }
        }
    }

    record Source(String title, String uri) {}

    public void shutdown() {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }
}
