# Spec: CLI 交互特效增强 v2

## 1. 需求

仿照 Claude Code 的交互风格，实现两个核心特效：
1. **输入框**：用 `╭─`/`╰─` 半开放边框包裹用户输入区域
2. **Thinking 动画**：模型思考时显示旋转动画 spinner，直到 LLM 返回结果

## 2. 目标效果

```
╭────────────────────────────────────────────╮
│  ✻ zclaw v1.0                             │
│                                            │
│  Model: claude-opus-4-6-v1                 │
│  Work dir: ~/IdeaProjects/my-project       │
│                                            │
│  Type /help for commands                   │
╰────────────────────────────────────────────╯

╭─────────────────────────────────────────────
│  > 用户输入问题_
╰─────────────────────────────────────────────

  ⠹ Thinking...          ← 旋转动画，原地覆盖

  ✻ zclaw

  Java 有几个流行的 Agent 框架...

  ⚡ read_file            ← tool call 输出（已有）

  ⠼ Thinking...          ← 每次 llmClient.chat() 前都显示

  ✻ zclaw

  最终回复内容...

  [tokens: 1234 in / 567 out]

╭─────────────────────────────────────────────
│  > 下一个问题_
╰─────────────────────────────────────────────
```

## 3. 设计细节

### 3.1 输入框（精简上下留白）

去掉输入行上下的空 `│` 行，输入框仅保留三行结构：

```
╭─────────────────────────────────────────────
│  > 用户输入_
╰─────────────────────────────────────────────
```

对应代码调整：
- `printInputBoxTop()`: 只打印 `╭─...`，不再打印空 `│` 行
- `closeInputBox()`: 只打印 `╰─...`，不再打印空 `│` 行
- prompt 保持 `│  > `

### 3.2 Thinking 动画

#### Spinner 字符序列

使用 Braille 点阵字符实现旋转效果：
```
⠋ ⠙ ⠹ ⠸ ⠼ ⠴ ⠦ ⠧ ⠇ ⠏
```
每 80ms 切换一帧，循环播放。

#### ThinkingIndicator 类

新建 `com.zxx.zclaw.cli.ThinkingIndicator`：

```java
public class ThinkingIndicator {
    private static final String[] FRAMES = {"⠋","⠙","⠹","⠸","⠼","⠴","⠦","⠧","⠇","⠏"};
    private static final long INTERVAL_MS = 80;
    private static final String CYAN = "\u001B[36m";
    private static final String RESET = "\u001B[0m";

    private final PrintStream out;
    private volatile boolean running;
    private Thread spinnerThread;

    public ThinkingIndicator(PrintStream out) { this.out = out; }

    /** 开始显示 spinner（非阻塞，启动后台线程） */
    public void start() {
        running = true;
        spinnerThread = new Thread(() -> {
            int i = 0;
            while (running) {
                out.print("\r  " + CYAN + FRAMES[i % FRAMES.length] + RESET + " Thinking...");
                out.flush();
                i++;
                try { Thread.sleep(INTERVAL_MS); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
            }
            // 清除 spinner 行
            out.print("\r" + " ".repeat(30) + "\r");
            out.flush();
        }, "thinking-indicator");
        spinnerThread.setDaemon(true);
        spinnerThread.start();
    }

    /** 停止 spinner */
    public void stop() {
        running = false;
        if (spinnerThread != null) {
            try { spinnerThread.join(200); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }
}
```

#### 关键行为

- `start()` 启动后台 daemon 线程，循环打印 `\r  ⠹ Thinking...`
- `stop()` 设置 `running=false`，等待线程退出，清除当前行
- 使用 `\r`（回车符）实现原地覆盖，不产生新行
- 线程为 daemon，不阻止 JVM 退出

### 3.3 AgentLoop 集成

在 `AgentLoop` 中添加 `ThinkingIndicator` 字段，在每次 `llmClient.chat()` 调用前后控制：

```java
public class AgentLoop {
    private ThinkingIndicator thinkingIndicator;

    public AgentLoop(...) {
        ...
        this.thinkingIndicator = new ThinkingIndicator(out);
    }

    public String processInput(String userInput) throws IOException {
        conversation.addMessage(Message.user(userInput));

        for (int iteration = 0; ...) {
            thinkingIndicator.start();        // ← 开始 spinner
            LLMResponse response = llmClient.chat(...);
            thinkingIndicator.stop();         // ← 停止 spinner

            // ... 其余逻辑不变 ...

            // tool calls 执行完后，下一轮循环会再次 start()
        }
    }
}
```

### 3.4 AgentCli 调整

- 保持现有输入框逻辑
- `✻ zclaw` 标识移到 AgentLoop 的 thinking indicator 停止后、输出响应前
- 实际上 `✻ zclaw` 仍由 AgentCli 在 `processInput` 返回前打印

## 4. 修改文件清单

| 文件 | 操作 |
|------|------|
| `src/main/java/com/zxx/zclaw/cli/ThinkingIndicator.java` | **新建** |
| `src/main/java/com/zxx/zclaw/agent/AgentLoop.java` | 修改：集成 ThinkingIndicator |
| `src/main/java/com/zxx/zclaw/cli/AgentCli.java` | 微调（如有需要） |
| `src/test/java/com/zxx/zclaw/cli/ThinkingIndicatorTest.java` | **新建**：单元测试 |

## 5. 验收标准

1. 输入框仅三行：`╭─...` + `│  > 输入` + `╰─...`，无多余空行
2. 提示符为 `│  > `，带颜色
3. **每次 `llmClient.chat()` 期间，终端显示旋转 spinner + "Thinking..."**
4. **spinner 在 LLM 返回后立即消失，不留残余字符**
5. **多轮 tool call 时，每次 LLM 调用都有独立的 thinking 动画**
6. 助理回复前显示 `✻ zclaw` 标识
7. slash command 输出不显示 thinking 动画
8. Ctrl+C / Ctrl+D 正常工作
9. 现有测试全部通过
10. ThinkingIndicator 有单元测试
