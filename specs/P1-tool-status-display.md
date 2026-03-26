# P1: 工具调用状态展示

## 需求概述

在终端中优雅地展示每个工具调用的执行结果，包括：
- **Bash 输出**：长输出截断 + 行数统计
- **文件路径**：read/write/edit 文件操作时突出显示路径
- **Diff 摘要**：edit_file 和 write_file 显示增删行数
- **匹配数**：grep 和 glob 显示匹配数量

## 现状分析

当前 `AgentLoop.java` 第 116 行只打印：
```java
out.println("\u001B[36m⚡ " + toolName + "\u001B[0m");
```

工具结果只通过日志记录（`log.info("Tool result: {} chars", result.length())`），用户看不到执行摘要。

## 实现方案

### 1. 新建 `ToolResultFormatter` 类

位置：`src/main/java/com/zxx/zcode/cli/ToolResultFormatter.java`

负责将工具执行结果格式化为用户友好的展示字符串。

### 2. 修改 `AgentLoop.java`

在执行工具后，调用 `ToolResultFormatter` 生成展示信息并输出。

### 3. 具体格式化规则

#### bash
- 输出总行数
- 截断策略：超过 20 行时，显示前 10 行 + `... X more lines ...` + 后 5 行
- 如果是错误（输出包含 `[Exit code: X]`），特殊标记

#### read_file
- 显示文件路径和总行数
- 截断策略：超过 50 行时，显示前 20 行 + `... X more lines ...` + 后 10 行

#### write_file
- 显示文件路径
- 显示写入字符数

#### edit_file
- 显示文件路径
- **Diff 摘要**：解析 old_string 和 new_string 的行数差异，显示 `+X -Y lines`

#### glob
- 显示匹配文件数量
- 截断策略：超过 15 个文件时，显示前 10 个 + `... X more files ...`
- 每个文件单独一行

#### grep
- 显示匹配行数
- 截断策略：超过 30 行时，显示前 15 行 + `... X more matches ...`

### 4. 展示格式示例

```
⚡ bash: ls -la
  ✓ 15 lines
  drwxr-xr-x  12 cc  staff   384 Mar 25 10:00 .
  drwxr-xr-x   5 cc  staff   160 Mar 25 09:30 ..
  -rw-r--r--   1 cc  staff  1024 Mar 25 10:00 README.md
  ... 12 more lines ...

⚡ read_file: src/main/java/Example.java
  ✓ 248 lines (showing 1-30, 218 more lines hidden)
     1	package com.example;
     2	public class Example {
  ...

⚡ write_file: src/main/java/NewFile.java
  ✓ 1,234 chars written → NewFile.java

⚡ edit_file: src/main/java/Example.java
  ✓ +12 -3 lines → Example.java

⚡ glob: **/*.java
  ✓ 42 files matched (showing 1-10, 32 more files hidden)
  src/main/java/A.java
  src/main/java/B.java
  ... 32 more files ...

⚡ grep: "public class"
  ✓ 15 matches in 8 files (showing 1-15, more hidden)
  src/main/java/A.java:3: public class A
  src/main/java/B.java:10: public class B
  ... more matches ...
```

## 验收标准

1. 每个工具调用后，在终端显示格式化的执行摘要
2. 长输出自动截断，不刷屏
3. Diff 摘要能正确计算行数差异
4. 匹配类工具显示总匹配数
5. 所有格式化输出使用 ANSI 颜色区分（工具名青色、路径绿色、统计信息灰色）
6. 不影响 LLM 收到的完整工具结果（格式化只用于展示）
7. 单元测试覆盖 `ToolResultFormatter` 的所有格式化逻辑

## 文件变更

| 文件 | 操作 |
|------|------|
| `src/main/java/com/zxx/zcode/cli/ToolResultFormatter.java` | 新增 |
| `src/main/java/com/zxx/zcode/agent/AgentLoop.java` | 修改 |
| `src/test/java/com/zxx/zcode/cli/ToolResultFormatterTest.java` | 新增 |
