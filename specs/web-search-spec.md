# Web Search Feature Spec

## 1. 背景与问题

### 1.1 需求
z-code 在做技术方案时需要具备联网搜索能力，能够查询实时信息、GitHub 高星项目、技术文档等，提供专家级分析（参考 Claude Code 的 WebSearch 能力）。

### 1.2 核心问题：API 格式不兼容

Claude 的内置 web search 是 **Anthropic Messages API 的原生功能**，使用专有的工具类型和响应格式。而 z-code 当前使用的是 **OpenAI 兼容格式**（`/v1/chat/completions`）调用 Ctrip AI Gateway。

| 维度 | 当前 z-code（OpenAI 格式） | Claude Web Search（Anthropic 格式） |
|------|--------------------------|-------------------------------------|
| 端点 | `/v1/chat/completions` | `/v1/messages` |
| 工具声明 | `{"type": "function", "function": {...}}` | `{"type": "web_search_20250305", "name": "web_search", ...}` |
| 响应格式 | `choices[0].message.content` | `content: [{type: "text"}, {type: "server_tool_use"}, ...]` |
| 工具调用 | `tool_calls[].function.name/arguments` | `content` 数组中的 `server_tool_use` block |
| 工具结果 | z-code 本地执行后发回 | 服务端自动执行，结果直接在响应的 `web_search_tool_result` block 中返回 |
| Auth Header | `Authorization: Bearer {key}` | `x-api-key: {key}` + `anthropic-version: 2023-06-01` |

**结论：无法在 OpenAI 兼容格式上使用 Claude 内置 web search，必须支持 Anthropic Messages API。**

---

## 2. 功能设计

### 2.1 总体方案

引入 **Anthropic Messages API Client**，与现有 OpenAI 兼容 Client 并存，通过配置切换：

```
┌─────────────┐
│  AgentLoop   │  ← 统一的 agent 循环逻辑
└──────┬───────┘
       │ 调用 LLMClient 接口
       ▼
┌──────────────────┐
│   LLMClient      │  ← 抽象接口
│   (interface)     │
├──────────────────┤
│                  │
▼                  ▼
┌──────────┐  ┌────────────────┐
│ OpenAI   │  │  Anthropic     │
│ Client   │  │  Client        │
│ (现有)    │  │ (新增,含web    │
│          │  │  search支持)   │
└──────────┘  └────────────────┘
```

### 2.2 用户体验

- 用户在 `~/.zcode/config.json` 中配置 `api_provider` 为 `"anthropic"`（或保持 `"openai"` 使用现有 Gateway）
- 配置 Anthropic API key 后，web search 自动可用
- LLM 自动决定何时搜索（与 Claude Code 行为一致）
- 搜索结果带来源 URL 引用，用户可以验证信息
- z-code 控制台输出搜索过程日志（搜索词、结果数量、来源等）

### 2.3 配置变更

`~/.zcode/config.json` 新增字段：

```json
{
  "api_key": "sk-ant-xxx",
  "base_url": "https://api.anthropic.com",
  "model": "claude-sonnet-4-20250514",
  "api_provider": "anthropic",
  "web_search_enabled": true,
  "web_search_max_uses": 5,
  "web_search_allowed_domains": [],
  "web_search_blocked_domains": []
}
```

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `api_provider` | string | `"openai"` | API 提供者：`"openai"` 或 `"anthropic"` |
| `web_search_enabled` | boolean | `true` | 是否启用 web search（仅 anthropic 模式生效） |
| `web_search_max_uses` | int | `5` | 单次请求最大搜索次数 |
| `web_search_allowed_domains` | string[] | `[]` | 搜索白名单域名（与 blocked 互斥） |
| `web_search_blocked_domains` | string[] | `[]` | 搜索黑名单域名（与 allowed 互斥） |

CLI 参数新增：`--api-provider=anthropic`

环境变量新增：`ZCODE_API_PROVIDER`

---

## 3. 细节实现

### 3.1 提取 LLMClient 接口

将现有 `LLMClient` 重构为接口，当前实现改名为 `OpenAILLMClient`：

```java
// 新接口
public interface LLMClient {
    /**
     * 发送聊天请求。
     * @param messages 对话历史
     * @param toolDefs 工具定义列表（客户端工具）
     * @return 统一的响应对象
     */
    LLMResponse chat(List<Message> messages, List<ToolDefinition> toolDefs) throws IOException;

    void shutdown();
}
```

### 3.2 统一响应模型 `LLMResponse`

由于 OpenAI 和 Anthropic 响应格式差异大，引入统一响应模型：

```java
public class LLMResponse {
    private String textContent;              // 最终文本回复
    private List<ToolCallInfo> toolCalls;     // 需要客户端执行的工具调用
    private List<WebSearchResult> webSearchResults;  // web search 结果（仅展示用）
    private Usage usage;
    private Message rawAssistantMessage;     // 原始 assistant 消息（用于加入对话历史）

    public boolean hasToolCalls() { ... }
    public boolean hasWebSearchResults() { ... }
}

public class ToolCallInfo {
    private String id;
    private String name;
    private String argumentsJson;
}

public class WebSearchResult {
    private String query;          // 搜索词
    private String url;            // 来源 URL
    private String title;          // 页面标题
    private String pageAge;        // 页面时效
    private String encryptedContent; // 加密内容（Anthropic 服务端用）
}
```

### 3.3 OpenAILLMClient（重构自现有 LLMClient）

逻辑不变，只是实现新接口，返回 `LLMResponse`。web search 相关字段为空。

### 3.4 AnthropicLLMClient（新增）

#### 3.4.1 请求格式

```java
public class AnthropicChatRequest {
    private String model;
    private int max_tokens = 8192;
    private List<AnthropicMessage> messages;
    private String system;                    // system prompt 单独字段
    private List<Object> tools;               // 混合工具列表（function tools + server tools）
}
```

发送的 JSON 示例：

```json
{
  "model": "claude-sonnet-4-20250514",
  "max_tokens": 8192,
  "system": "You are z-code...",
  "messages": [
    {"role": "user", "content": "搜索一下 Java Agent 框架有哪些"}
  ],
  "tools": [
    {
      "type": "web_search_20250305",
      "name": "web_search",
      "max_uses": 5
    },
    {
      "name": "read_file",
      "description": "Read file contents...",
      "input_schema": {
        "type": "object",
        "properties": { "file_path": {"type": "string"} },
        "required": ["file_path"]
      }
    }
  ]
}
```

注意 Anthropic 格式与 OpenAI 的区别：
- `system` 是顶层字段，不是 messages 中的 system role
- 客户端工具用 `input_schema`（不是 `parameters`），且没有外层 `type: "function"` 包装
- web search 工具用 `type: "web_search_20250305"`，是服务端工具
- Header：`x-api-key` + `anthropic-version: 2023-06-01` + `content-type: application/json`

#### 3.4.2 响应解析

Anthropic 的响应 `content` 是一个混合类型的数组：

```json
{
  "id": "msg_xxx",
  "role": "assistant",
  "content": [
    {
      "type": "server_tool_use",
      "id": "srvtoolu_xxx",
      "name": "web_search",
      "input": {"query": "Java Agent framework GitHub stars 2025"}
    },
    {
      "type": "web_search_tool_result",
      "tool_use_id": "srvtoolu_xxx",
      "content": [
        {
          "type": "web_search_result",
          "url": "https://github.com/langchain4j/langchain4j",
          "title": "langchain4j - Java LLM framework",
          "encrypted_content": "ENC[...]",
          "page_age": "3 days ago"
        }
      ]
    },
    {
      "type": "text",
      "text": "根据搜索结果，以下是 Java Agent 框架..."
    },
    {
      "type": "tool_use",
      "id": "toolu_xxx",
      "name": "read_file",
      "input": {"file_path": "pom.xml"}
    }
  ],
  "stop_reason": "tool_use",
  "usage": {
    "input_tokens": 500,
    "output_tokens": 200
  }
}
```

**关键 content block 类型：**

| type | 说明 | 处理方式 |
|------|------|---------|
| `text` | LLM 文本输出 | 提取为 `textContent` |
| `tool_use` | 客户端工具调用（如 read_file） | 提取为 `ToolCallInfo`，需本地执行 |
| `server_tool_use` | 服务端工具调用（web search） | 日志输出搜索词，无需本地执行 |
| `web_search_tool_result` | 服务端搜索结果 | 提取为 `WebSearchResult` 供展示 |

解析逻辑（伪代码）：

```java
LLMResponse parseAnthropicResponse(AnthropicChatResponse resp) {
    LLMResponse result = new LLMResponse();
    StringBuilder text = new StringBuilder();
    List<ToolCallInfo> toolCalls = new ArrayList<>();
    List<WebSearchResult> searchResults = new ArrayList<>();

    for (ContentBlock block : resp.getContent()) {
        switch (block.getType()) {
            case "text":
                text.append(block.getText());
                break;
            case "tool_use":
                // 客户端工具调用 → 需要 AgentLoop 执行
                toolCalls.add(new ToolCallInfo(block.getId(), block.getName(), block.getInput()));
                break;
            case "server_tool_use":
                // 服务端工具（web search）→ 仅日志
                log.info("🔍 Web Search: " + block.getInput().get("query"));
                break;
            case "web_search_tool_result":
                // 搜索结果 → 展示 + 日志
                for (SearchResult sr : block.getSearchResults()) {
                    searchResults.add(new WebSearchResult(sr));
                    log.info("  📄 " + sr.getTitle() + " - " + sr.getUrl());
                }
                break;
        }
    }

    result.setTextContent(text.toString());
    result.setToolCalls(toolCalls);
    result.setWebSearchResults(searchResults);
    result.setRawAssistantMessage(buildAssistantMessage(resp));
    result.setUsage(convertUsage(resp.getUsage()));
    return result;
}
```

#### 3.4.3 对话历史管理（Anthropic 格式）

Anthropic Messages API 的 assistant 消息的 `content` 是完整的 block 数组（包含 `server_tool_use`、`web_search_tool_result`、`text`、`tool_use`），需要**原样**放回对话历史，否则 API 会报错。

工具结果消息格式（客户端工具执行后发回）：

```json
{
  "role": "user",
  "content": [
    {
      "type": "tool_result",
      "tool_use_id": "toolu_xxx",
      "content": "file contents here..."
    }
  ]
}
```

注意：Anthropic 格式中，tool result 的 role 是 `"user"`（不是 OpenAI 的 `"tool"`）。

### 3.5 Message 模型改造

当前 `Message` 类的 `content` 是 `String`，但 Anthropic 格式中 `content` 可以是字符串或对象数组。改造方案：

```java
public class Message {
    private String role;
    private Object content;  // String（OpenAI）或 List<ContentBlock>（Anthropic）
    // ... 保留现有字段兼容 OpenAI 格式

    // Anthropic 专用工厂方法
    public static Message anthropicAssistant(List<ContentBlock> contentBlocks) { ... }
    public static Message anthropicToolResult(String toolUseId, String result) { ... }
}
```

使用 Gson 自定义序列化器处理 `content` 字段的多态问题。

### 3.6 AgentLoop 改造

主循环逻辑变化不大，主要适配 `LLMResponse`：

```java
public String processInput(String userInput) throws IOException {
    conversation.addMessage(Message.user(userInput));

    for (int iteration = 0; iteration < MAX_TOOL_ITERATIONS; iteration++) {
        LLMResponse response = llmClient.chat(conversation.getMessages(), getToolDefs());

        // 将原始 assistant 消息加入对话历史
        conversation.addMessage(response.getRawAssistantMessage());

        // 输出 web search 结果日志
        if (response.hasWebSearchResults()) {
            for (WebSearchResult r : response.getWebSearchResults()) {
                out.println("\u001B[33m🔍 [" + r.getTitle() + "] " + r.getUrl() + "\u001B[0m");
            }
        }

        if (!response.hasToolCalls()) {
            // 无工具调用 → 最终文本回复
            printUsage(response);
            return response.getTextContent();
        }

        // 执行客户端工具
        for (ToolCallInfo tc : response.getToolCalls()) {
            out.println("\u001B[36m⚡ " + tc.getName() + "\u001B[0m");
            String result = toolRegistry.execute(tc.getName(), tc.getArgumentsJson());
            conversation.addMessage(
                llmClient.createToolResultMessage(tc.getId(), result)
            );
        }
    }

    return "[Max tool iterations reached]";
}
```

### 3.7 LLMClientFactory

根据配置创建对应的 Client：

```java
public class LLMClientFactory {
    public static LLMClient create(AgentConfig config) {
        String provider = config.getApiProvider();
        switch (provider) {
            case "anthropic":
                log.info("Using Anthropic Messages API (web search enabled: "
                    + config.isWebSearchEnabled() + ")");
                return new AnthropicLLMClient(config);
            case "openai":
            default:
                log.info("Using OpenAI-compatible API");
                return new OpenAILLMClient(config);
        }
    }
}
```

### 3.8 日志输出规范

所有交互必须有日志输出，所有异常必须输出完整信息：

```
[INFO] API Provider: anthropic
[INFO] Web Search: enabled (max_uses=5)
[INFO] → POST https://api.anthropic.com/v1/messages (model=claude-sonnet-4-20250514)
[INFO] ← 200 OK (input_tokens=523, output_tokens=1847)
[INFO] 🔍 Web Search query: "Java Agent framework GitHub stars 2025"
[INFO]   📄 langchain4j - https://github.com/langchain4j/langchain4j
[INFO]   📄 Spring AI - https://github.com/spring-projects/spring-ai
[INFO] ⚡ Tool call: read_file({"file_path": "pom.xml"})
[INFO] ✅ Tool result: 2345 chars

[ERROR] LLM API error: HTTP 429 Too Many Requests
        URL: https://api.anthropic.com/v1/messages
        Response: {"error":{"type":"rate_limit_error","message":"..."}}
        java.io.IOException: LLM API error 429: ...
            at com.zxx.zcode.llm.AnthropicLLMClient.chat(AnthropicLLMClient.java:85)
            at com.zxx.zcode.agent.AgentLoop.processInput(AgentLoop.java:78)
            ...
```

---

## 4. 新增/修改文件清单

### 新增文件

| 文件 | 说明 |
|------|------|
| `llm/LLMClient.java` | LLM 客户端接口（从原类提取） |
| `llm/OpenAILLMClient.java` | 原 LLMClient 重命名，实现新接口 |
| `llm/AnthropicLLMClient.java` | Anthropic Messages API 客户端 |
| `llm/LLMClientFactory.java` | 根据配置创建 Client |
| `llm/model/LLMResponse.java` | 统一响应模型 |
| `llm/model/ToolCallInfo.java` | 统一的工具调用信息 |
| `llm/model/WebSearchResult.java` | Web search 结果模型 |
| `llm/model/ContentBlock.java` | Anthropic content block 模型 |
| `llm/model/AnthropicChatRequest.java` | Anthropic 请求模型 |
| `llm/model/AnthropicChatResponse.java` | Anthropic 响应模型 |

### 修改文件

| 文件 | 修改内容 |
|------|---------|
| `config/AgentConfig.java` | 新增 `apiProvider`、`webSearchEnabled`、`webSearchMaxUses` 等字段 |
| `llm/model/Message.java` | `content` 改为 `Object` 类型，支持 String 和 List<ContentBlock>；新增 Anthropic 工厂方法 |
| `agent/AgentLoop.java` | 使用 `LLMClient` 接口 + `LLMResponse`；新增 web search 结果输出 |
| `ZCodeMain.java` | 使用 `LLMClientFactory` 创建 Client |
| `agent/AgentLoop.java` → `buildSystemPrompt()` | 新增 web_search 工具说明 |

---

## 5. 实施步骤

### Step 1: 提取 LLMClient 接口 + 重构现有代码
- 提取 `LLMClient` 接口
- 现有实现重命名为 `OpenAILLMClient`
- 引入 `LLMResponse` 统一模型
- AgentLoop 适配新接口
- **验收：所有现有功能正常工作，无回归**

### Step 2: 新增 AnthropicLLMClient
- 实现 Anthropic Messages API 请求/响应
- 处理 Anthropic 格式的工具声明（`input_schema` 代替 `parameters`）
- 解析 content block 数组（text/tool_use/server_tool_use/web_search_tool_result）
- 处理 Anthropic 格式的对话历史（tool_result 角色为 user）
- **验收：切换 api_provider=anthropic 后，普通对话 + 工具调用正常**

### Step 3: 集成 Web Search
- 在 Anthropic 请求中加入 `web_search_20250305` 工具声明
- 解析 web search 结果并展示
- 配置项支持（enabled/max_uses/domains）
- 完善日志输出
- **验收：提问实时信息时，LLM 自动搜索并返回带引用的回答**

### Step 4: 配置 & 错误处理
- AgentConfig 新增所有配置字段
- CLI 参数 + 环境变量支持
- 完善异常处理和错误信息输出
- **验收：配置切换顺畅，错误信息清晰完整**

---

## 6. 单元测试计划

### 6.1 AnthropicLLMClient 测试

| 测试用例 | 说明 |
|---------|------|
| `testBuildRequest_basicChat` | 验证基础对话请求 JSON 格式正确 |
| `testBuildRequest_withTools` | 验证工具定义转换为 Anthropic `input_schema` 格式 |
| `testBuildRequest_withWebSearch` | 验证 web search 工具声明正确 |
| `testBuildRequest_systemPromptExtraction` | 验证 system message 提取为顶层 `system` 字段 |
| `testBuildRequest_headers` | 验证 `x-api-key` 和 `anthropic-version` header |
| `testParseResponse_textOnly` | 解析纯文本响应 |
| `testParseResponse_withToolUse` | 解析包含 `tool_use` 的响应 |
| `testParseResponse_withWebSearch` | 解析包含 `server_tool_use` + `web_search_tool_result` + `text` 的混合响应 |
| `testParseResponse_webSearchNoResults` | 搜索无结果时的处理 |
| `testParseResponse_multipleSearches` | 多次搜索的解析 |
| `testErrorHandling_httpError` | HTTP 错误码处理，验证完整错误信息输出 |
| `testErrorHandling_malformedJson` | 响应 JSON 格式错误时的异常处理 |
| `testErrorHandling_networkTimeout` | 网络超时的异常处理 |

### 6.2 LLMResponse 测试

| 测试用例 | 说明 |
|---------|------|
| `testHasToolCalls` | 有/无工具调用的判断 |
| `testHasWebSearchResults` | 有/无搜索结果的判断 |
| `testGetTextContent` | 文本内容提取 |

### 6.3 OpenAILLMClient 测试（回归）

| 测试用例 | 说明 |
|---------|------|
| `testBuildRequest_unchanged` | 验证重构后请求格式不变 |
| `testParseResponse_unchanged` | 验证重构后响应解析不变 |

### 6.4 AgentLoop 测试

| 测试用例 | 说明 |
|---------|------|
| `testProcessInput_withWebSearch` | 模拟 web search 响应，验证日志输出和最终结果 |
| `testProcessInput_webSearchPlusToolCalls` | 搜索结果 + 客户端工具调用混合场景 |
| `testProcessInput_openaiCompatibility` | OpenAI 模式下功能不受影响 |

### 6.5 AgentConfig 测试

| 测试用例 | 说明 |
|---------|------|
| `testDefaultValues` | 默认值正确（apiProvider=openai, webSearchEnabled=true） |
| `testLoadFromConfigFile` | 从 config.json 加载新字段 |
| `testEnvOverride` | 环境变量覆盖 |
| `testCliOverride` | CLI 参数覆盖 |

### 6.6 Message 测试

| 测试用例 | 说明 |
|---------|------|
| `testAnthropicAssistantMessage` | content 为 ContentBlock 列表的序列化 |
| `testAnthropicToolResultMessage` | tool_result 格式正确（role=user） |
| `testOpenaiMessageUnchanged` | OpenAI 格式消息不受影响 |

---

## 7. 验收标准

### 功能验收

1. **配置切换**：`api_provider` 在 `"openai"` 和 `"anthropic"` 间切换，两种模式下基础对话 + 6 个工具均正常工作
2. **Web Search 自动触发**：在 anthropic 模式下，提问实时信息（如"今天的科技新闻"）时，LLM 自动进行 web search
3. **搜索结果展示**：控制台输出搜索词和结果来源（URL + 标题）
4. **引用准确**：LLM 回答中引用的信息可以通过来源 URL 验证
5. **混合调用**：LLM 可以在同一次对话中同时使用 web search 和本地工具（如先搜索再读文件）
6. **配置生效**：`max_uses`、`allowed_domains`、`blocked_domains` 参数生效
7. **OpenAI 模式无回归**：切换回 `api_provider=openai` 后所有现有功能正常

### 日志验收

8. **启动日志**：启动时输出 API Provider 类型和 web search 启用状态
9. **请求日志**：每次 API 调用输出 URL、model
10. **响应日志**：每次响应输出 token 用量
11. **搜索日志**：每次 web search 输出搜索词 + 结果列表（标题+URL）
12. **工具日志**：每次工具调用输出工具名和参数摘要
13. **错误日志**：所有异常输出完整信息（HTTP 状态码 + 响应体 + 堆栈）

### 测试验收

14. 所有单元测试通过
15. 测试覆盖率：新增代码 >= 80%

---

## 8. 风险与注意事项

1. **Ctrip AI Gateway 兼容性**：Gateway 是否支持 Anthropic Messages API 格式（`/v1/messages`）未知。若支持，可直接用 Gateway；若不支持，需直连 Anthropic API（需要额外的 Anthropic API Key）
2. **费用**：Web search 有额外的搜索费用（$10/1000 次搜索），需在文档中提示用户
3. **encrypted_content**：Anthropic 返回的搜索结果内容是加密的，仅供模型内部使用，z-code 不需要也无法解密
4. **Anthropic API 版本**：当前使用 `anthropic-version: 2023-06-01`，web search 工具类型版本为 `web_search_20250305`，后续版本可能变更
5. **Message content 多态**：Anthropic 的 `content` 字段可以是 string 或 array，需要 Gson 自定义反序列化器，这是实现中最容易出错的部分
