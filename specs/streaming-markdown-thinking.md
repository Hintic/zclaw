# Streaming, Markdown & Thinking Strip — Spec

## 1. 去掉思考内容（Thinking Strip）

**目标**：Anthropic API 返回的 `thinking` block 不输出到终端。

| 组件 | 文件 | 改动 |
|------|------|------|
| `ContentBlock.java` | `src/main/java/com/zxx/zcode/llm/model/ContentBlock.java` | 新增 `thinking` 类型 |
| `AnthropicChatResponse.java` | `src/main/java/com/zxx/zcode/llm/model/AnthropicChatResponse.java` | 解析 `type: "thinking"` → 丢弃 |
| `LLMResponse.java` | `src/main/java/com/zxx/zcode/llm/model/LLMResponse.java` | 过滤掉 `thinking` block 内容 |

**验收标准**：
- [ ] 启动 `java -jar target/z-code-1.0-SNAPSHOT.jar --work-dir=. --api-provider=anthropic` 向 Agent 提问（如果模型返回 thinking block）
- [ ] thinking block 内容**不显示**在终端输出中

---

## 2. Markdown 终端渲染

**目标**：LLM 返回的 markdown 内容在终端正确渲染（代码块高亮、标题加粗等）。

**方案**：使用 [MarkdownWriter](https://github.com/voidwell/markdownwriter) 或类似 ANSI-compatible markdown 渲染库。

| 组件 | 文件 | 改动 |
|------|------|------|
| `pom.xml` | `pom.xml` | 新增 `com.github.voidwell:markdownwriter:1.0.0` 依赖 |
| `MarkdownRenderer.java` | `src/main/java/com/zxx/zcode/cli/MarkdownRenderer.java` | **新建** — 渲染 markdown 到 ANSI 转义序列 |
| `AgentCli.java` | `src/main/java/com/zxx/zcode/cli/AgentCli.java` | 替换 `out.println(response)` 为 `markdownRenderer.render(response)` |

**验收标准**：
- [ ] 代码块（\`\`\`）有语法高亮（语言标识符识别但不强制做完整语法高亮，基础颜色区分即可）
- [ ] 标题（`#` / `##` / `###`）加粗显示
- [ ] 行内代码（`` ` ``）高亮显示
- [ ] 链接和图片不崩溃（链接显示为 `text` 形式）
- [ ] **流式输出时**：已渲染内容**实时滚动显示**，不等到 token 结束才一次性渲染

---

## 3. 流式输出（SSE Streaming）

**目标**：LLM 响应以流式方式逐 token 输出，用户实时看到内容，无需等待完整响应。

| 组件 | 文件 | 改动 |
|------|------|------|
| `LLMClient.java` | `src/main/java/com/zxx/zcode/llm/LLMClient.java` | 新增 `chatStream()` 方法（返回 `Iterator<LLMResponse>` 或 `Flux<String>` 的等价物） |
| `OpenAILLMClient.java` | `src/main/java/com/zxx/zcode/llm/OpenAILLMClient.java` | 实现 SSE 版本的 `chatStream()` |
| `AnthropicLLMClient.java` | `src/main/java/com/zxx/zcode/llm/AnthropicLLMClient.java` | 实现 SSE 版本的 `chatStream()`（使用 `text/event-stream` Accept header） |
| `AgentLoop.java` | `src/main/java/com/zxx/zcode/agent/AgentLoop.java` | 工具调用仍用同步 `chat()`；普通回复切换为 `chatStream()` |
| `ThinkingIndicator.java` | `src/main/java/com/zxx/zcode/cli/ThinkingIndicator.java` | 流式输出时改为显示 "Streaming..." 进度指示（替代旋转动画） |

**流式行为**：
- SSE 连接建立 → 清空 "Thinking..." 行 → 开始逐字输出
- 遇到 markdown 需要**实时渲染**：输出一个 token 后立即调用 `MarkdownRenderer` 刷新该行/块
- 工具调用（`tool_use` block）检测到后**中断流式输出**，转为同步完整处理

**验收标准**：
- [ ] Anthropic API (`/v1/messages` with `anthropic-beta: streaming-2024-01-01`) 支持 SSE 流
- [ ] OpenAI 兼容 API (Ctrip Gateway) 支持 `/v1/chat/completions` SSE 流
- [ ] 流式输出时用户**实时看到字符逐个出现**（延迟 < 100ms/ token）
- [ ] 流式期间 **Ctrl+C 不崩溃**（优雅关闭 SSE 连接）

---

## 4. 实现顺序

1. **Streaming** → 这是基础设施，其他功能依赖它的实时刷新机制
2. **Thinking Strip** → 流式输出时就需要过滤 thinking block
3. **Markdown Renderer** → 在流式输出中实时渲染

---

## 5. 技术细节

### 5.1 SSE 流式解析

**Anthropic SSE** 格式（`text/event-stream`）：
```
event: content_block_delta
data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Hello"}}

event: message_delta
data: {"type":"message_delta","delta":{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"\n"}}}

event: message_stop
data: {"type":"message_stop"}
```

**OpenAI SSE** 格式：
```
data: {"choices":[{"delta":{"content":"Hello"}}]}
data: [DONE]
```

### 5.2 Markdown 渲染库选型

| 库 | 优点 | 缺点 |
|----|------|------|
| `com.github.voidwell:markdownwriter` | 轻量、ANSI 兼容 | 维护不活跃 |
| `com.github.nyaapantsu:ansi2html` | ANSI ↔ HTML | 不适合终端渲染 |
| 手写简单的 ANSI 渲染 | 零依赖 | 工作量大 |

**推荐**：`markdownwriter` 足够满足当前需求（代码块高亮 + 标题加粗），后续如需更丰富功能再考虑换库。

---

## 6. 风险与降级方案

| 风险 | 缓解措施 |
|------|----------|
| 流式输出时 markdown 渲染性能问题 | 批量刷新（每 10ms 最多刷新一次屏幕） |
| SSE 连接失败/超时 | fallback 到同步 `chat()` |
| thinking block 过滤不完整 | 在 `AgentLoop` 输出前增加统一过滤器 |
