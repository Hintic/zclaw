package com.zxx.zclaw.llm;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.zxx.zclaw.config.AgentConfig;
import com.zxx.zclaw.llm.model.*;
import com.zxx.zclaw.tool.ToolRegistry;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * OpenAI-compatible LLM client (for Ctrip AI Gateway, etc.).
 */
public class OpenAILLMClient implements LLMClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAILLMClient.class);
    private static final MediaType JSON_TYPE = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final Gson gson;
    private final AgentConfig config;

    public OpenAILLMClient(AgentConfig config) {
        this.config = config;
        this.gson = new GsonBuilder().disableHtmlEscaping().create();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        log.info("OpenAI-compatible client initialized, baseUrl={}, model={}", config.getBaseUrl(), config.getModel());
    }

    @Override
    public LLMResponse chat(List<Message> messages, ToolRegistry toolRegistry) throws IOException {
        List<ChatRequest.ToolDefinition> tools = toolRegistry != null
                ? toolRegistry.getToolDefinitions()
                : List.of();
        ChatRequest request = new ChatRequest(config.getModel(), messages, tools);
        String jsonBody = gson.toJson(request);

        String url = config.getBaseUrl().replaceAll("/+$", "") + "/v1/chat/completions";
        log.info("→ POST {} (model={})", url, config.getModel());

        Request.Builder reqBuilder = new Request.Builder()
                .url(url)
                .post(RequestBody.create(jsonBody, JSON_TYPE))
                .header("Content-Type", "application/json");

        if (config.getApiKey() != null && !config.getApiKey().isEmpty()) {
            reqBuilder.header("Authorization", "Bearer " + config.getApiKey());
        }

        try (Response response = httpClient.newCall(reqBuilder.build()).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "no body";
                String errorMsg = "LLM API error " + response.code() + ": " + errorBody;
                log.error("LLM API error: HTTP {} {}\n        URL: {}\n        Response: {}",
                        response.code(), response.message(), url, errorBody);
                throw new IOException(errorMsg);
            }
            String body = response.body().string();
            ChatResponse chatResponse = gson.fromJson(body, ChatResponse.class);
            if (chatResponse == null) {
                log.error("Failed to parse LLM response: {}", body);
                throw new IOException("Failed to parse LLM response: " + body);
            }
            LLMResponse result = convertResponse(chatResponse);
            log.info("← 200 OK (input_tokens={}, output_tokens={})", result.getInputTokens(), result.getOutputTokens());
            return result;
        }
    }

    @Override
    public void chatStream(List<Message> messages, ToolRegistry toolRegistry, StreamingConsumer consumer) throws IOException {
        List<ChatRequest.ToolDefinition> tools = toolRegistry != null
                ? toolRegistry.getToolDefinitions()
                : List.of();
        ChatRequest request = new ChatRequest(config.getModel(), messages, tools);
        request.setStream(true);
        String jsonBody = gson.toJson(request);

        String url = config.getBaseUrl().replaceAll("/+$", "") + "/v1/chat/completions";
        log.info("→ POST {} (stream=true, model={})", url, config.getModel());

        Request.Builder reqBuilder = new Request.Builder()
                .url(url)
                .post(RequestBody.create(jsonBody, JSON_TYPE))
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream");

        if (config.getApiKey() != null && !config.getApiKey().isEmpty()) {
            reqBuilder.header("Authorization", "Bearer " + config.getApiKey());
        }

        OkHttpClient streamClient = httpClient.newBuilder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .build();

        final StringBuilder fullContent = new StringBuilder();
        final AtomicReference<String> curToolId = new AtomicReference<>();
        final AtomicReference<String> curToolName = new AtomicReference<>();
        final AtomicReference<StringBuilder> curToolArgs = new AtomicReference<>(new StringBuilder());
        final int[] inputTokens = {0};
        final int[] outputTokens = {0};
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<Throwable> error = new AtomicReference<>();

        streamClient.newCall(reqBuilder.build()).enqueue(new Callback() {
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
                    // Decode lines as UTF-8. Per-byte (char) casting breaks CJK and other multi-byte text.
                    while (true) {
                        String line = source.readUtf8Line();
                        if (line == null) {
                            break;
                        }
                        if (line.startsWith("data: ")) {
                            String data = line.substring(6);
                            handleSseEvent(data, fullContent, curToolId, curToolName, curToolArgs,
                                    inputTokens, outputTokens, consumer);
                        }
                    }
                } catch (IOException e) {
                    // Only treat as error if we don't have content
                    if (error.get() == null) {
                        error.set(e);
                        consumer.onError(e);
                    }
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
            String msg = error.get().getMessage();
            throw new IOException("SSE stream error: " + (msg != null ? msg : error.get().toString()));
        }

        consumer.onComplete(inputTokens[0], outputTokens[0], Message.assistant(fullContent.toString(), null));
    }

    private void handleSseEvent(String data, StringBuilder fullContent,
                                AtomicReference<String> curToolId, AtomicReference<String> curToolName,
                                AtomicReference<StringBuilder> curToolArgs, int[] inputTokens, int[] outputTokens,
                                StreamingConsumer consumer) {
        if (data == null || data.isEmpty() || "[DONE]".equals(data.trim())) {
            return;
        }
        try {
            JsonObject obj = gson.fromJson(data, JsonObject.class);
            if (obj == null) return;

            var choices = obj.getAsJsonArray("choices");
            if (choices == null || choices.isEmpty()) return;
            JsonObject choice = choices.get(0).getAsJsonObject();
            JsonObject delta = choice.getAsJsonObject("delta");

            if (delta == null) return;

            // tool_calls delta
            if (delta.has("tool_calls")) {
                var tcArr = delta.getAsJsonArray("tool_calls");
                for (var tcEl : tcArr) {
                    JsonObject tc = tcEl.getAsJsonObject();
                    if (tc.has("id") && !tc.get("id").isJsonNull()) {
                        curToolId.set(tc.get("id").getAsString());
                    }
                    if (tc.has("function")) {
                        JsonObject fn = tc.getAsJsonObject("function");
                        if (fn.has("name") && !fn.get("name").isJsonNull()) {
                            curToolName.set(fn.get("name").getAsString());
                        }
                        if (fn.has("arguments") && !fn.get("arguments").isJsonNull()) {
                            curToolArgs.set(new StringBuilder(fn.get("arguments").getAsString()));
                        }
                    }
                }
                return;
            }

            // content delta
            if (delta.has("content")) {
                String text = delta.get("content").getAsString();
                if (text != null && !text.isEmpty()) {
                    fullContent.append(text);
                    if (!consumer.onTextDelta(text)) {
                        log.info("Streaming cancelled by consumer");
                    }
                }
            }

            // finish
            String finish = choice.has("finish_reason") && !choice.get("finish_reason").isJsonNull()
                    ? choice.get("finish_reason").getAsString() : null;
            if ("stop".equals(finish)) {
                if (curToolId.get() != null && curToolName.get() != null) {
                    StringBuilder args = curToolArgs.get();
                    consumer.onToolCall(curToolId.get(), curToolName.get(),
                            args != null ? args.toString() : "");
                }
            }
        } catch (Exception e) {
            log.warn("SSE parse error: {}", e.getMessage());
        }
    }

    @Override
    public Message createToolResultMessage(String toolCallId, String content) {
        return Message.toolResult(toolCallId, content);
    }

    @Override
    public void shutdown() {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
        log.info("OpenAI client shut down");
    }

    private LLMResponse convertResponse(ChatResponse chatResponse) {
        LLMResponse result = new LLMResponse();

        result.setTextContent(chatResponse.getContent());
        result.setRawAssistantMessage(chatResponse.getAssistantMessage());

        if (chatResponse.hasToolCalls()) {
            List<ToolCallInfo> toolCalls = new ArrayList<>();
            for (ToolCall tc : chatResponse.getToolCalls()) {
                toolCalls.add(new ToolCallInfo(tc.getId(), tc.getFunctionName(), tc.getFunctionArguments()));
            }
            result.setToolCalls(toolCalls);
        }

        if (chatResponse.getUsage() != null) {
            ChatResponse.Usage u = chatResponse.getUsage();
            result.setInputTokens(u.getPromptTokens());
            result.setOutputTokens(u.getCompletionTokens());
            result.setCacheReadTokens(u.getCachedPromptTokens());
            result.setCacheCreationTokens(0);
            result.setCacheHitDenominator(u.getPromptTokens());
        }

        return result;
    }
}
