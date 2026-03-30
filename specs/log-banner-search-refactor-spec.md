# Spec: 日志文件化 + Banner 重设计 + WebSearchTool 协议重构

## 1. 需求概述

三个独立需求：

1. **日志文件化**：运行 `zclaw` 时控制台不显示日志，日志保存到本地文件
2. **Banner 重设计**：仿 Claude Code 风格重新设计启动 banner
3. **WebSearchTool 协议重构**：从 Gemini 原生 API 改为 OpenAI `chat/completions` 通用协议

---

## 2. 需求一：日志文件化

### 2.1 现状

- 使用 `slf4j-simple`（直接输出到 stderr）
- 运行时控制台混杂日志和用户交互，体验差

### 2.2 方案

- 将 `slf4j-simple` 替换为 `logback-classic`
- 新增 `src/main/resources/logback.xml`
- 日志输出到 `~/.zclaw/logs/zclaw.log`
- 控制台零日志输出
- 使用 RollingFileAppender，按大小滚动

### 2.3 实现细节

#### pom.xml 改动

```xml
<!-- 移除 -->
<dependency>
    <groupId>org.slf4j</groupId>
    <artifactId>slf4j-simple</artifactId>
    <version>2.0.13</version>
</dependency>

<!-- 新增 -->
<dependency>
    <groupId>ch.qos.logback</groupId>
    <artifactId>logback-classic</artifactId>
    <version>1.5.6</version>
</dependency>
```

#### logback.xml

```xml
<configuration>
    <!-- 定义日志目录 -->
    <property name="LOG_DIR" value="${user.home}/.zclaw/logs" />

    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>${LOG_DIR}/zclaw.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy">
            <fileNamePattern>${LOG_DIR}/zclaw.%d{yyyy-MM-dd}.%i.log</fileNamePattern>
            <maxFileSize>10MB</maxFileSize>
            <maxHistory>7</maxHistory>
            <totalSizeCap>100MB</totalSizeCap>
        </rollingPolicy>
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <root level="INFO">
        <appender-ref ref="FILE" />
    </root>
</configuration>
```

#### 日志目录自动创建

Logback 的 `RollingFileAppender` 会自动创建父目录，无需手动 mkdir。

#### 影响范围

- `pom.xml`：替换依赖
- 新增 `src/main/resources/logback.xml`
- 所有现有 `log.info/warn/error` 调用无需任何改动（SLF4J facade 不变）

### 2.4 验收标准

1. 运行 `zclaw` 后控制台只显示 banner + prompt，无任何日志行
2. `~/.zclaw/logs/zclaw.log` 文件中能看到完整日志（启动信息、工具调用、错误堆栈）
3. 日志文件自动按 10MB 滚动，保留 7 天
4. 现有全部单元测试通过

---

## 3. 需求二：Banner 重设计

### 3.1 现状

```
╔══════════════════════════════════════╗
║            zclaw v1.0               ║
║     Your coding assistant CLI        ║
╚══════════════════════════════════════╝

Working directory: /path/to/project
Type /help for commands, or just start chatting.
```

过于简陋，与 Claude Code 风格差距大。

### 3.2 目标效果

仿 Claude Code 风格，简洁清晰，信息密度适中：

```
╭────────────────────────────────────────╮
│          ✻ zclaw v1.0                 │
│                                        │
│   Model: claude-opus-4-6-v1            │
│   Work dir: ~/IdeaProjects/my-project  │
│                                        │
│   Type /help for commands              │
╰────────────────────────────────────────╯
```

### 3.3 设计要点

1. **圆角边框**：`╭╮╰╯` 替代方角 `╔╗╚╝`，更现代
2. **品牌符号**：`✻` 类似 Claude Code 的品牌标识
3. **关键信息**：版本号 + 模型名 + 工作目录
4. **工作目录缩短**：将 `$HOME` 替换为 `~` 显示（更简洁）
5. **颜色**：边框和品牌用青色（`\033[36m`），信息用默认色

### 3.4 实现细节

修改 `AgentCli.java` 的 `printWelcome()` 方法。

需要新增构造参数或 setter 传入 `model` 名称（当前 `AgentCli` 不知道 model 信息）。

方案：`AgentCli` 构造函数新增 `AgentConfig config` 参数（或直接传 `String modelName`）。

```java
public AgentCli(AgentLoop agentLoop, AgentConfig config, PrintStream out) {
    this.agentLoop = agentLoop;
    this.workDir = config.getWorkDir();
    this.modelName = config.getModel();
    this.out = out;
}
```

`ZClawMain.java` 调用处相应修改。

`printWelcome()` 改为动态生成内容，自动计算边框宽度适配最长行。

### 3.5 影响范围

- 修改 `AgentCli.java`：构造函数 + `printWelcome()`
- 修改 `ZClawMain.java`：调用处传入 config

### 3.6 验收标准

1. 启动时显示圆角边框 banner，包含模型名和工作目录
2. 工作目录中 `$HOME` 部分替换为 `~`
3. 无多余空行、无日志混入
4. 现有单元测试通过

---

## 4. 需求三：WebSearchTool 协议重构

### 4.1 现状

当前使用 Gemini 原生 API 格式：

```
POST {base_url}/v1beta/models/{model}:generateContent
Authorization: Bearer {api_key}

{
  "contents": [{"parts": [{"text": "Search for: ..."}]}],
  "tools": [{"google_search": {}}]
}
```

响应格式为 Gemini 原生（`candidates[].content.parts[].text` + `groundingMetadata`）。

### 4.2 目标

改为 OpenAI 标准 `chat/completions` 协议：

```
POST {base_url}/v1/chat/completions
Authorization: Bearer {api_key}

{
  "model": "gemini-2.5-flash",
  "messages": [{"role": "user", "content": "Search for: ..."}],
  "tools": [{"google_search": {}}]
}
```

这样 Gateway 侧使用统一的 OpenAI 兼容路由，模型路由靠 `model` 字段区分，无需 Gemini 原生路径。

### 4.3 响应格式变化

OpenAI 兼容格式的响应：

```json
{
  "id": "chatcmpl-xxx",
  "object": "chat.completion",
  "model": "gemini-2.5-flash",
  "choices": [
    {
      "index": 0,
      "message": {
        "role": "assistant",
        "content": "搜索结果摘要文本..."
      },
      "finish_reason": "stop"
    }
  ]
}
```

**关键问题**：`groundingMetadata`（来源 URL）在 OpenAI 兼容格式中如何返回？

可能的情况：
1. Gateway 将 `groundingMetadata` 放在 `choices[0]` 的扩展字段中
2. Gateway 将来源 URL 内联到 `content` 文本中
3. Gateway 不返回 grounding 信息

**应对策略**：
- 先解析 `choices[0].message.content` 获取搜索文本
- 尝试从响应 JSON 中提取 `groundingMetadata`（如果 Gateway 透传）
- 如果没有结构化 metadata，尝试从 `content` 文本中提取 URL（正则匹配）
- 兜底：仅返回文本摘要，不附加结构化来源

### 4.4 实现细节

#### WebSearchTool 改动

1. **`buildUrl()`**：
   - 旧：`{base_url}/v1beta/models/{model}:generateContent`
   - 新：`{base_url}/v1/chat/completions`

2. **`buildRequestBody(query)`**：
   - 旧：Gemini 原生格式（contents + tools）
   - 新：OpenAI chat/completions 格式（model + messages + tools）

```java
String buildRequestBody(String query) {
    Map<String, Object> body = Map.of(
        "model", searchModel,
        "messages", List.of(
            Map.of("role", "user", "content", "Search for: " + query)
        ),
        "tools", List.of(
            Map.of("google_search", Map.of())
        )
    );
    return gson.toJson(body);
}
```

3. **`parseAndFormat(query, responseBody)`**：
   - 先尝试 OpenAI 格式解析（`choices[0].message.content`）
   - 再尝试提取 `groundingMetadata`（如果 Gateway 透传）
   - 如果两种都失败，回退到 Gemini 原生格式解析（兼容）

4. **响应模型**：
   - 新增 `ChatCompletionSearchResponse` 类（或复用现有 `ChatResponse` 并扩展）
   - 保留 `GeminiResponse` 作为回退

#### 新增响应模型

```java
public class SearchResponse {
    // OpenAI 格式
    private List<Choice> choices;
    // Gemini 透传（可能存在）
    private List<GeminiResponse.Candidate> candidates;

    public static class Choice {
        private Message message;
        private GroundingMetadata groundingMetadata; // Gateway 可能透传
    }

    public static class Message {
        private String role;
        private String content;
    }
}
```

实际结构取决于 Gateway 返回的格式，实现时需先测试真实 Gateway 响应。

### 4.5 测试改动

所有 `WebSearchToolTest` 中的 mock 响应需改为 OpenAI chat/completions 格式：

- `testExecute_success`：mock OpenAI 格式响应
- `testBuildUrl`：断言 URL 为 `/v1/chat/completions`
- `testBuildRequestBody`：断言包含 `model` 和 `messages` 字段
- 其他错误测试保持类似逻辑

`GeminiResponse` 模型类视情况保留或移除。

### 4.6 影响范围

- 修改 `WebSearchTool.java`：URL、请求体、响应解析
- 新增或修改响应模型类
- 修改 `WebSearchToolTest.java`：所有 mock 响应改为 OpenAI 格式
- 可能修改或移除 `GeminiResponse.java` 和 `GeminiResponseTest.java`

### 4.7 验收标准

1. WebSearchTool 发送请求到 `{base_url}/v1/chat/completions`
2. 请求体为 OpenAI 标准 chat/completions 格式，含 `model` + `messages` + `tools`
3. 能正确解析 Gateway 返回的响应，提取搜索文本
4. 如有 grounding 来源信息，正确提取并格式化
5. 所有错误处理场景仍正常工作
6. 所有单元测试通过

---

## 5. 实施顺序

建议按依赖关系依次实施：

1. **需求一（日志文件化）** — 独立，先做，做完后续调试体验好
2. **需求二（Banner 重设计）** — 独立，改 UI 层
3. **需求三（WebSearchTool 协议重构）** — 改核心逻辑，需要测试 Gateway 实际响应

---

## 6. 总测试计划

| 范围 | 新增/修改测试 |
|------|-------------|
| 日志文件化 | 无需新增测试（logback 配置验证靠手动 + 现有测试通过） |
| Banner | 可选：AgentCli 构造函数测试 |
| WebSearchTool | 修改全部 19 个测试的 mock 响应格式 + 新增 OpenAI 格式解析测试 |

现有 72 个测试必须全部通过。
