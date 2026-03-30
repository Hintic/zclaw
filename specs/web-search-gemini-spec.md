# Web Search Feature Spec (Gemini Google Search)

## 1. 背景

### 1.1 需求
zclaw 需要联网搜索能力，用于查询实时信息、GitHub 高星项目、技术文档等。

### 1.2 方案演进
- **方案 1（已弃用）**：Anthropic 原生 `web_search_20250305` 服务端工具 → Ctrip Gateway 不支持服务端工具透传，搜索变成了空壳的客户端 tool_use
- **方案 2（本 Spec）**：使用 Gemini API 内置 Google Search grounding 做搜索，搜索结果返回给 Claude 做分析

### 1.3 核心思路

```
用户提问 → Claude 决定调用 web_search 工具
                ↓
    zclaw 本地拦截 web_search tool call
                ↓
    调用 Gemini API (with google_search grounding)
                ↓
    提取搜索结果文本 + 来源 URL
                ↓
    作为 tool result 返回给 Claude
                ↓
    Claude 基于搜索结果生成最终回答
```

**优势**：
- 纯客户端工具，与 LLM Provider 无关（OpenAI / Anthropic 模式均可用）
- 复用现有 Tool 接口和 ToolRegistry 机制
- **复用现有 `base_url` 和 `api_key`**，通过 Ctrip Gateway 统一路由，无需单独配 Gemini key
- Google Search 质量高、覆盖广

---

## 2. 功能设计

### 2.1 架构

```
┌──────────────┐
│   AgentLoop   │
└──────┬───────┘
       │ LLMClient.chat() → 返回 tool_use: "web_search"
       │
       │ toolRegistry.execute("web_search", args)
       ▼
┌──────────────────┐
│  WebSearchTool   │ ← implements Tool
│  (新增)           │
└──────┬───────────┘
       │ HTTP POST (复用 base_url + api_key)
       ▼
┌──────────────────────────────────────┐
│  Ctrip AI Gateway → Gemini           │
│  POST {base_url}/v1beta/models/      │
│       {web_search_model}             │
│       :generateContent               │
│  tools: [{"google_search": {}}]      │
└──────────────────────────────────────┘
       │
       ▼
  返回 groundingMetadata:
  - groundingChunks[].web.uri / web.title
  - 模型生成的 grounded text
       │
       ▼
  格式化为 tool result 字符串 → 返回 Claude
```

### 2.2 用户体验

1. 用户在 `~/.zclaw/config.json` 中配置 `web_search_model`（可选，默认 `gemini-2.5-flash`）
2. 启动 zclaw 时，若 `web_search_enabled=true`，自动注册 `web_search` 工具
3. WebSearchTool **复用现有 `base_url` 和 `api_key`**，通过 Gateway 路由到 Gemini 模型
4. Claude 根据问题自行决定是否调用搜索（通过 system prompt 中的工具描述引导）
5. 搜索过程在控制台输出日志（搜索词、来源 URL、结果摘要长度）
6. 最终回答由 Claude 生成，包含搜索来源引用

### 2.3 配置

`~/.zclaw/config.json` 新增字段：

```json
{
  "api_key": "...",
  "base_url": "http://aigw.fx.ctripcorp.com/llm/100000420",
  "model": "claude-opus-4-6-v1",
  "api_provider": "openai",
  "web_search_enabled": true,
  "web_search_model": "gemini-2.5-flash"
}
```

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `web_search_enabled` | boolean | `true` | 是否启用 web search |
| `web_search_model` | string | `"gemini-2.5-flash"` | 用于搜索的 Gemini 模型（走同一个 Gateway） |

CLI 参数：`--web-search-model=gemini-2.0-flash`

**关键设计**：不新增 API key 和 base_url 配置，WebSearchTool 复用 `config.getBaseUrl()` 和 `config.getApiKey()`，请求路径改为 Gemini 格式（`/v1beta/models/{model}:generateContent`）。

---

## 3. 细节实现

### 3.1 WebSearchTool

新增 `src/main/java/com/zxx/zclaw/tool/WebSearchTool.java`，实现 `Tool` 接口：

```java
public class WebSearchTool implements Tool {
    private static final Logger log = LoggerFactory.getLogger(WebSearchTool.class);
    private final OkHttpClient httpClient;
    private final String baseUrl;      // 复用 config.getBaseUrl()
    private final String apiKey;       // 复用 config.getApiKey()
    private final String searchModel;  // 默认 "gemini-2.5-flash"

    @Override
    public String name() { return "web_search"; }

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
        // 调用 Gemini API，返回格式化结果
    }
}
```

### 3.2 Gemini API 调用

#### 请求

```
POST {base_url}/v1beta/models/{web_search_model}:generateContent
Header: Authorization: Bearer {api_key}
Header: Content-Type: application/json
```

**说明**：复用 `base_url` 和 `api_key`，通过 Gateway 路由到 Gemini 模型。Auth header 使用与 OpenAI 相同的 `Authorization: Bearer` 格式（Gateway 统一鉴权）。

请求体：

```json
{
  "contents": [
    {
      "parts": [
        {"text": "Search for: {query}"}
      ]
    }
  ],
  "tools": [
    {"google_search": {}}
  ]
}
```

#### 响应解析

```json
{
  "candidates": [
    {
      "content": {
        "parts": [{"text": "搜索结果摘要文本..."}],
        "role": "model"
      },
      "groundingMetadata": {
        "webSearchQueries": ["actual query 1", "actual query 2"],
        "groundingChunks": [
          {"web": {"uri": "https://...", "title": "Source Title"}},
          {"web": {"uri": "https://...", "title": "Another Source"}}
        ],
        "groundingSupports": [
          {
            "segment": {"startIndex": 0, "endIndex": 100, "text": "..."},
            "groundingChunkIndices": [0]
          }
        ]
      }
    }
  ]
}
```

提取逻辑：
1. 从 `candidates[0].content.parts[0].text` 获取 Gemini 生成的搜索摘要
2. 从 `candidates[0].groundingMetadata.groundingChunks` 获取来源 URL 和标题
3. 从 `candidates[0].groundingMetadata.webSearchQueries` 获取实际搜索词（日志用）

### 3.3 Tool Result 格式

将 Gemini 搜索结果格式化为纯文本返回给 Claude：

```
## Web Search Results for: "{query}"

### Summary
{Gemini 生成的搜索摘要文本}

### Sources
1. [Source Title 1] https://example.com/page1
2. [Source Title 2] https://example.com/page2
3. [Source Title 3] https://example.com/page3

---
Use the above search results to answer the user's question. Cite sources when referencing specific information.
```

这样 Claude 可以直接基于这些信息生成回答，并引用来源。

### 3.4 Gemini 响应模型

新增 `src/main/java/com/zxx/zclaw/llm/model/GeminiResponse.java`：

```java
public class GeminiResponse {
    private List<Candidate> candidates;

    public static class Candidate {
        private Content content;
        private GroundingMetadata groundingMetadata;
    }

    public static class Content {
        private List<Part> parts;
        private String role;
    }

    public static class Part {
        private String text;
    }

    public static class GroundingMetadata {
        private List<String> webSearchQueries;
        private List<GroundingChunk> groundingChunks;
        private List<GroundingSupport> groundingSupports;
    }

    public static class GroundingChunk {
        private Web web;
    }

    public static class Web {
        private String uri;
        private String title;
    }

    public static class GroundingSupport {
        private Segment segment;
        private List<Integer> groundingChunkIndices;
    }

    public static class Segment {
        private int startIndex;
        private int endIndex;
        private String text;
    }
}
```

### 3.5 AgentConfig 改造

```java
// 新增字段
private String webSearchModel = "gemini-2.5-flash"; // 搜索模型

// config.json 读取
if (map.containsKey("web_search_model")) {
    this.webSearchModel = (String) map.get("web_search_model");
}

// CLI 参数
// --web-search-model=gemini-2.0-flash
```

### 3.6 ZClawMain 改造

```java
// 工具注册（在现有 6 个工具之后）
if (config.isWebSearchEnabled()) {
    toolRegistry.register(new WebSearchTool(
        config.getBaseUrl(),    // 复用
        config.getApiKey(),     // 复用
        config.getWebSearchModel()
    ));
    log.info("Web search enabled (model: {}, baseUrl: {})",
        config.getWebSearchModel(), config.getBaseUrl());
} else {
    log.info("Web search disabled");
}
```

### 3.7 System Prompt 调整

当 web_search 工具已注册时，在 system prompt 中添加引导：

```
You have access to a web_search tool that can search the internet for real-time information.
Use it when the user asks about current events, recent releases, live documentation, or any
information that might be newer than your training data. When you use search results in your
answer, cite the source URLs.
```

这已经在现有的 `AgentLoop.buildSystemPrompt()` 中有条件添加，无需大改（只需检查 toolRegistry 是否包含 "web_search" 工具）。

### 3.8 日志输出

```
[INFO] Web search enabled (model: gemini-2.5-flash, baseUrl: http://aigw.fx.ctripcorp.com/llm/100000420)
[INFO] 🔍 WebSearchTool executing: query="Java Agent framework 2025"
[INFO] → POST http://aigw.fx.ctripcorp.com/llm/100000420/v1beta/models/gemini-2.5-flash:generateContent
[INFO] ← 200 OK (Gemini search completed)
[INFO]   Actual queries: ["Java Agent framework 2025", "Java AI agent libraries"]
[INFO]   Sources: 3 results
[INFO]     📄 langchain4j - https://github.com/langchain4j/langchain4j
[INFO]     📄 Spring AI - https://spring.io/projects/spring-ai
[INFO]     📄 Semantic Kernel Java - https://github.com/microsoft/semantic-kernel
[INFO]   Summary: 847 chars
[INFO] ✅ WebSearchTool result: 1523 chars

[ERROR] WebSearchTool error: HTTP 429 Too Many Requests
        URL: http://aigw.fx.ctripcorp.com/llm/100000420/v1beta/models/gemini-2.5-flash:generateContent
        Response body: {"error":{"code":429,"message":"Resource has been exhausted..."}}
        java.io.IOException: Gemini API error 429: Resource has been exhausted
            at com.zxx.zclaw.tool.WebSearchTool.execute(WebSearchTool.java:87)
```

### 3.9 错误处理

| 错误场景 | 处理方式 |
|---------|---------|
| `web_search_enabled=false` | 不注册 WebSearchTool，日志提示 |
| HTTP 4xx/5xx | 返回 `"Error: Gemini API error {code}: {message}"` 作为 tool result |
| 网络超时（30s） | 返回 `"Error: Gemini API request timed out after 30s"` |
| 响应无 groundingMetadata | 仅返回 Gemini 生成的文本，不附加来源 |
| 响应无 groundingChunks | 返回文本 + 搜索词，不附加来源 URL |
| JSON 解析失败 | 返回 `"Error: Failed to parse Gemini response: {detail}"` |

所有错误都返回 error 字符串（而非抛异常），让 Claude 看到错误信息并决定下一步（重试或换方式回答）。

---

## 4. 新增/修改文件清单

### 新增文件

| 文件 | 说明 |
|------|------|
| `tool/WebSearchTool.java` | Web 搜索工具，调用 Gemini API |
| `llm/model/GeminiResponse.java` | Gemini API 响应模型 |

### 修改文件

| 文件 | 修改内容 |
|------|---------|
| `config/AgentConfig.java` | 新增 `webSearchModel` 字段及读取逻辑 |
| `ZClawMain.java` | 条件注册 WebSearchTool |

**不需要修改**：`LLMClient`、`AgentLoop`、`ToolRegistry`、`Message`——因为 WebSearchTool 是标准的客户端 Tool，完全复用现有机制。

---

## 5. 实施步骤

### Step 1: AgentConfig 扩展
- 新增 `webSearchModel` 字段（默认 `"gemini-2.5-flash"`）
- 支持 config.json、CLI 参数（`--web-search-model`）
- 单元测试

### Step 2: GeminiResponse 模型
- 新增 Gemini 响应反序列化模型
- 单元测试（JSON 反序列化）

### Step 3: WebSearchTool 实现
- 实现 Tool 接口（name/description/parameters/execute）
- Gemini API HTTP 调用（OkHttp）
- 响应解析 + 结果格式化
- 完善日志和错误处理
- 单元测试（MockWebServer 模拟 Gemini API）

### Step 4: ZClawMain 集成
- 条件注册 WebSearchTool
- 端到端手动测试

---

## 6. 单元测试计划

### 6.1 WebSearchTool 测试（MockWebServer）

| 测试用例 | 说明 |
|---------|------|
| `testName` | 返回 "web_search" |
| `testDescription` | 描述非空 |
| `testParameters` | JSON schema 包含 query 参数 |
| `testExecute_success` | 正常搜索返回格式化结果（含摘要 + 来源 URL） |
| `testExecute_noGroundingMetadata` | 响应无 groundingMetadata，仅返回文本 |
| `testExecute_noGroundingChunks` | 有 metadata 无 chunks，返回文本 + 搜索词 |
| `testExecute_emptyQuery` | 空查询参数处理 |
| `testExecute_httpError_429` | HTTP 429 返回错误信息字符串 |
| `testExecute_httpError_401` | API key 无效时的错误信息 |
| `testExecute_httpError_500` | 服务端错误 |
| `testExecute_timeout` | 网络超时处理 |
| `testExecute_malformedJson` | 响应 JSON 格式错误 |
| `testExecute_multipleChunks` | 多个搜索来源的格式化 |
| `testExecute_multipleQueries` | Gemini 执行多个搜索词 |

### 6.2 GeminiResponse 测试

| 测试用例 | 说明 |
|---------|------|
| `testDeserialize_fullResponse` | 完整响应反序列化 |
| `testDeserialize_minimalResponse` | 最小响应（无 groundingMetadata） |
| `testDeserialize_emptyChunks` | groundingChunks 为空数组 |

### 6.3 AgentConfig 测试（扩展现有）

| 测试用例 | 说明 |
|---------|------|
| `testDefaultWebSearchModel` | 默认值为 "gemini-2.5-flash" |
| `testWebSearchModel_fromConfig` | 从 config.json 读取 |
| `testWebSearchModel_fromCli` | 从 CLI 参数读取 |

---

## 7. 验收标准

### 功能验收

1. **搜索触发**：Claude 在遇到需要实时信息的问题时，自动调用 `web_search` 工具
2. **搜索结果**：Gemini 返回相关搜索摘要和来源 URL
3. **Claude 分析**：Claude 基于搜索结果生成高质量回答，并引用来源
4. **Provider 无关**：`api_provider=openai` 和 `api_provider=anthropic` 两种模式下 web search 均可用
5. **零额外配置**：只需现有的 `base_url` + `api_key`，搜索模型默认 `gemini-2.5-flash`
6. **现有功能无回归**：6 个已有工具功能不受影响

### 日志验收

7. **启动日志**：启动时输出 web search 是否启用及使用的 Gemini 模型
8. **搜索日志**：每次搜索输出查询词、请求 URL、响应状态
9. **结果日志**：输出搜索来源数量和各来源的标题+URL
10. **错误日志**：所有异常输出完整信息（HTTP 状态码 + 响应体 + 堆栈）

### 测试验收

11. 所有单元测试通过（`mvn test`）
12. 新增测试 >= 17 个（WebSearchTool 14 + GeminiResponse 3）

---

## 8. 与旧方案的关系

- 旧方案（`specs/web-search-spec.md`）中实现的 Anthropic 双客户端架构（LLMClient 接口、OpenAILLMClient、AnthropicLLMClient）**保留不动**
- Anthropic 客户端中的 `web_search_20250305` 服务端工具声明可保留（未来 Gateway 支持时可直接启用），但当前不生效
- 本方案新增的 `WebSearchTool` 是完全独立的客户端工具，通过 `web_search_enabled` 控制启用
- 两者不冲突：即使在 anthropic 模式下，WebSearchTool 作为客户端工具也会被正确声明和执行
