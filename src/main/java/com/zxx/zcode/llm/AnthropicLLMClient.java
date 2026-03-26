package com.zxx.zcode.llm;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.zxx.zcode.config.AgentConfig;
import com.zxx.zcode.llm.model.*;
import com.zxx.zcode.tool.ToolRegistry;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Anthropic Messages API client with built-in web search support.
 */
public class AnthropicLLMClient implements LLMClient {

    private static final Logger log = LoggerFactory.getLogger(AnthropicLLMClient.class);
    private static final MediaType JSON_TYPE = MediaType.parse("application/json; charset=utf-8");
    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final int MAX_TOKENS = 8192;

    private final OkHttpClient httpClient;
    private final Gson gson;
    private final AgentConfig config;

    public AnthropicLLMClient(AgentConfig config) {
        this.config = config;
        this.gson = new GsonBuilder().disableHtmlEscaping().create();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(180, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        log.info("Anthropic client initialized, baseUrl={}, model={}, webSearch={}",
                config.getBaseUrl(), config.getModel(), config.isWebSearchEnabled());
    }

    @Override
    public LLMResponse chat(List<Message> messages, ToolRegistry toolRegistry) throws IOException {
        // Extract system message and convert messages to Anthropic format
        String systemPrompt = null;
        List<AnthropicChatRequest.AnthropicMessage> anthropicMessages = new ArrayList<>();

        for (Message msg : messages) {
            if ("system".equals(msg.getRole())) {
                systemPrompt = (String) msg.getContent();
            } else if ("assistant".equals(msg.getRole())) {
                anthropicMessages.add(convertAssistantMessage(msg));
            } else if ("tool".equals(msg.getRole())) {
                // OpenAI-style tool result → Anthropic-style (merge into last user message or create new)
                addToolResultToMessages(anthropicMessages, msg);
            } else {
                // user message
                anthropicMessages.add(new AnthropicChatRequest.AnthropicMessage(
                        msg.getRole(), msg.getContent()));
            }
        }

        // Build tools list
        List<Object> tools = buildToolsList(toolRegistry);

        AnthropicChatRequest request = new AnthropicChatRequest(
                config.getModel(), MAX_TOKENS, systemPrompt, anthropicMessages, tools);

        String jsonBody = gson.toJson(request);

        String url = config.getBaseUrl().replaceAll("/+$", "") + "/v1/messages";
        log.info("→ POST {} (model={})", url, config.getModel());

        Request.Builder reqBuilder = new Request.Builder()
                .url(url)
                .post(RequestBody.create(jsonBody, JSON_TYPE))
                .header("Content-Type", "application/json")
                .header("x-api-key", config.getApiKey())
                .header("anthropic-version", ANTHROPIC_VERSION);

        try (Response response = httpClient.newCall(reqBuilder.build()).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                log.error("Anthropic API error: HTTP {} {}\n        URL: {}\n        Response: {}",
                        response.code(), response.message(), url, body);
                throw new IOException("Anthropic API error " + response.code() + ": " + body);
            }

            AnthropicChatResponse chatResponse = gson.fromJson(body, AnthropicChatResponse.class);
            if (chatResponse == null) {
                log.error("Failed to parse Anthropic response: {}", body);
                throw new IOException("Failed to parse Anthropic response: " + body);
            }

            LLMResponse result = parseResponse(chatResponse);
            log.info("← 200 OK (input_tokens={}, output_tokens={})",
                    result.getInputTokens(), result.getOutputTokens());
            return result;
        }
    }

    @Override
    public void chatStream(List<Message> messages, ToolRegistry toolRegistry, StreamingConsumer consumer) throws IOException {
        String systemPrompt = null;
        List<AnthropicChatRequest.AnthropicMessage> anthropicMessages = new ArrayList<>();

        for (Message msg : messages) {
            if ("system".equals(msg.getRole())) {
                systemPrompt = (String) msg.getContent();
            } else if ("assistant".equals(msg.getRole())) {
                anthropicMessages.add(convertAssistantMessage(msg));
            } else if ("tool".equals(msg.getRole())) {
                addToolResultToMessages(anthropicMessages, msg);
            } else {
                anthropicMessages.add(new AnthropicChatRequest.AnthropicMessage(
                        msg.getRole(), msg.getContent()));
            }
        }

        List<Object> tools = buildToolsList(toolRegistry);
        AnthropicChatRequest request = new AnthropicChatRequest(
                config.getModel(), MAX_TOKENS, systemPrompt, anthropicMessages, tools);
        String jsonBody = gson.toJson(request);

        String url = config.getBaseUrl().replaceAll("/+$", "") + "/v1/messages";
        log.info("→ POST {} (stream=true, model={})", url, config.getModel());

        Request.Builder reqBuilder = new Request.Builder()
                .url(url)
                .post(RequestBody.create(jsonBody, JSON_TYPE))
                .header("Content-Type", "application/json")
                .header("x-api-key", config.getApiKey())
                .header("anthropic-version", ANTHROPIC_VERSION)
                .header("anthropic-beta", "streaming-2024-01-01")
                .header("Accept", "text/event-stream");

        OkHttpClient streamClient = httpClient.newBuilder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .build();

        Request rf = reqBuilder.build();
        final StringBuilder fullContent = new StringBuilder();
        final AtomicReference<String> curToolId = new AtomicReference<>();
        final AtomicReference<String> curToolName = new AtomicReference<>();
        final AtomicReference<StringBuilder> curToolArgs = new AtomicReference<>(new StringBuilder());
        final int[] inputTokens = {0};
        final int[] outputTokens = {0};
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<Throwable> error = new AtomicReference<>();

        streamClient.newCall(rf).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                error.set(e);
                consumer.onError(e);
                latch.countDown();
            }

            @Override
            public void onResponse(Call call, Response response) {
                if (!response.isSuccessful()) {
                    try (ResponseBody b = response.body()) {
                        String err = b != null ? b.string() : "no body";
                        error.set(new IOException("HTTP " + response.code() + ": " + err));
                        consumer.onError(error.get());
                    } catch (IOException ex) {
                        error.set(ex);
                        consumer.onError(ex);
                    }
                    latch.countDown();
                    return;
                }
                ResponseBody body = response.body();
                if (body == null) {
                    latch.countDown();
                    return;
                }
                try {
                    okio.BufferedSource source = body.source();
                    String currentEvent = null;
                    // Decode lines as UTF-8. Per-byte (char) casting breaks CJK and other multi-byte text.
                    while (true) {
                        String line = source.readUtf8Line();
                        if (line == null) {
                            break;
                        }
                        if (line.startsWith("event: ")) {
                            currentEvent = line.substring(7).trim();
                        } else if (line.startsWith("data: ")) {
                            String data = line.substring(6).trim();
                            if (!data.isEmpty()) {
                                handleAnthropicSseEvent(currentEvent, data, consumer,
                                        fullContent, curToolId, curToolName, curToolArgs);
                            }
                        }
                    }
                } catch (IOException e) {
                    error.set(e);
                    consumer.onError(e);
                } finally {
                    latch.countDown();
                }
            }
        });

        try {
            latch.await(300, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Streaming interrupted");
        }

        if (error.get() != null) {
            throw new IOException("SSE stream error: " + error.get().getMessage());
        }

        consumer.onComplete(inputTokens[0], outputTokens[0], Message.assistant(fullContent.toString(), null));
    }

    private void handleAnthropicSseEvent(String eventType, String data,
            StreamingConsumer consumer, StringBuilder fullContent,
            AtomicReference<String> curToolId, AtomicReference<String> curToolName,
            AtomicReference<StringBuilder> curToolArgs) {
        try {
            JsonObject obj = gson.fromJson(data, JsonObject.class);
            if (obj == null) return;

            String type = obj.get("type").getAsString();

            if ("content_block_delta".equals(type)) {
                int index = obj.get("index").getAsInt();
                JsonObject delta = obj.getAsJsonObject("delta");

                String deltaType = delta.get("type").getAsString();
                if ("text_delta".equals(deltaType)) {
                    String text = delta.get("text").getAsString();
                    if (text != null && !text.isEmpty()) {
                        fullContent.append(text);
                        consumer.onTextDelta(text);
                    }
                } else if ("thinking_delta".equals(deltaType)) {
                    // Strip thinking content silently — don't pass to consumer
                    log.debug("Thinking delta received, stripped");
                } else if ("input_json_delta".equals(deltaType)) {
                    String partial = delta.get("partial_json").getAsString();
                    curToolArgs.set(new StringBuilder(partial));
                }
            } else if ("message_delta".equals(type)) {
                JsonObject delta = obj.getAsJsonObject("delta");
                if (delta.has("content_block_delta")) {
                    JsonObject cbDelta = delta.getAsJsonObject("content_block_delta");
                    String cbType = cbDelta.get("type").getAsString();
                    if ("input_json_delta".equals(cbType)) {
                        String partial = cbDelta.get("partial_json").getAsString();
                        curToolArgs.set(new StringBuilder(partial));
                    }
                }
                if (obj.has("usage")) {
                    JsonObject usage = obj.getAsJsonObject("usage");
                    // output_tokens accumulates here
                }
            } else if ("message_stop".equals(type)) {
                // emit accumulated tool call if any
                if (curToolId.get() != null && curToolName.get() != null) {
                    StringBuilder args = curToolArgs.get();
                    consumer.onToolCall(curToolId.get(), curToolName.get(),
                            args != null ? args.toString() : "");
                }
            }
        } catch (Exception e) {
            log.warn("Anthropic SSE parse error (event={}): {}", eventType, e.getMessage());
        }
    }

    @Override
    public Message createToolResultMessage(String toolCallId, String content) {
        // Anthropic format: role=user, content=[{type: "tool_result", tool_use_id: ..., content: ...}]
        Map<String, Object> toolResult = new LinkedHashMap<>();
        toolResult.put("type", "tool_result");
        toolResult.put("tool_use_id", toolCallId);
        toolResult.put("content", content);

        Message msg = Message.anthropicToolResult(List.of(toolResult));
        return msg;
    }

    @Override
    public void shutdown() {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
        log.info("Anthropic client shut down");
    }

    private AnthropicChatRequest.AnthropicMessage convertAssistantMessage(Message msg) {
        // If the message has anthropicContent (raw content blocks), use it directly
        if (msg.getAnthropicContent() != null) {
            return new AnthropicChatRequest.AnthropicMessage("assistant", msg.getAnthropicContent());
        }
        // Otherwise, convert simple text to Anthropic format
        if (msg.getContent() != null) {
            return new AnthropicChatRequest.AnthropicMessage("assistant", msg.getContent());
        }
        return new AnthropicChatRequest.AnthropicMessage("assistant", "");
    }

    private void addToolResultToMessages(List<AnthropicChatRequest.AnthropicMessage> messages, Message msg) {
        // In Anthropic format, tool results are sent as user messages with content array
        Map<String, Object> toolResult = new LinkedHashMap<>();
        toolResult.put("type", "tool_result");
        toolResult.put("tool_use_id", msg.getToolCallId());
        toolResult.put("content", msg.getContent());

        // Check if the last message is already a user message with tool_result content
        if (!messages.isEmpty()) {
            AnthropicChatRequest.AnthropicMessage last = messages.get(messages.size() - 1);
            if ("user".equals(last.getRole()) && last.getContent() instanceof List) {
                @SuppressWarnings("unchecked")
                List<Object> contentList = (List<Object>) last.getContent();
                contentList.add(toolResult);
                return;
            }
        }

        // Create new user message with tool_result
        List<Object> content = new ArrayList<>();
        content.add(toolResult);
        messages.add(new AnthropicChatRequest.AnthropicMessage("user", content));
    }

    private List<Object> buildToolsList(ToolRegistry toolRegistry) {
        List<Object> tools = new ArrayList<>();

        // Add web search tool if enabled
        if (config.isWebSearchEnabled()) {
            tools.add(new AnthropicChatRequest.WebSearchToolDef(
                    config.getWebSearchMaxUses(),
                    config.getWebSearchAllowedDomains(),
                    config.getWebSearchBlockedDomains()));
            log.info("Web search tool added (max_uses={})", config.getWebSearchMaxUses());
        }

        // Add client-side tools in Anthropic format
        if (toolRegistry != null) {
            for (var toolDef : toolRegistry.getToolDefinitions()) {
                tools.add(new AnthropicChatRequest.AnthropicToolDef(
                        toolDef.getName(), toolDef.getDescription(), toolDef.getParameters()));
            }
        }

        return tools;
    }

    private LLMResponse parseResponse(AnthropicChatResponse resp) {
        LLMResponse result = new LLMResponse();
        StringBuilder textBuilder = new StringBuilder();
        List<ToolCallInfo> toolCalls = new ArrayList<>();
        List<WebSearchResult> searchResults = new ArrayList<>();

        // Track current web search query for associating with results
        String currentSearchQuery = null;

        if (resp.getContent() != null) {
            for (ContentBlock block : resp.getContent()) {
                if (block == null || block.getType() == null) continue;

                switch (block.getType()) {
                    case "text":
                        if (block.getText() != null) {
                            textBuilder.append(block.getText());
                        }
                        break;

                    case "tool_use":
                        // Client-side tool call → needs local execution
                        String inputJson = gson.toJson(block.getInput());
                        toolCalls.add(new ToolCallInfo(block.getId(), block.getName(), inputJson));
                        log.info("Tool use: {}({})", block.getName(), inputJson);
                        break;

                    case "server_tool_use":
                        // Server-side tool (web search) → log only
                        if (block.getInput() != null) {
                            currentSearchQuery = (String) block.getInput().get("query");
                            log.info("🔍 Web Search query: \"{}\"", currentSearchQuery);
                        }
                        break;

                    case "web_search_tool_result":
                        // Search results → extract for display
                        if (block.getContent() != null) {
                            for (ContentBlock.SearchResultEntry entry : block.getContent()) {
                                if ("web_search_result".equals(entry.getType())) {
                                    searchResults.add(new WebSearchResult(
                                            currentSearchQuery,
                                            entry.getUrl(),
                                            entry.getTitle(),
                                            entry.getPageAge()));
                                    log.info("  📄 {} - {}", entry.getTitle(), entry.getUrl());
                                }
                            }
                        }
                        break;

                    case "thinking":
                        // Strip thinking blocks — do not include in output
                        log.debug("Thinking block stripped ({} chars)",
                                block.getThinking() != null ? block.getThinking().length() : 0);
                        break;

                    default:
                        log.warn("Unknown content block type: {}", block.getType());
                        break;
                }
            }
        }

        result.setTextContent(textBuilder.toString());
        result.setToolCalls(toolCalls);
        result.setWebSearchResults(searchResults);

        // Build raw assistant message preserving full content blocks for conversation history
        result.setRawAssistantMessage(Message.anthropicAssistant(resp.getContent()));

        if (resp.getUsage() != null) {
            AnthropicChatResponse.Usage u = resp.getUsage();
            int in = u.getInputTokens();
            int cr = u.getCacheReadInputTokens();
            int cc = u.getCacheCreationInputTokens();
            result.setInputTokens(in);
            result.setOutputTokens(u.getOutputTokens());
            result.setCacheReadTokens(cr);
            result.setCacheCreationTokens(cc);
            result.setCacheHitDenominator(in + cr + cc);
        }

        return result;
    }
}
