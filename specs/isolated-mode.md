# Spec: 隔离模式 (Null Mode)

## 需求
在 zclaw 对话过程中，通过斜杠命令切换隔离模式：

- `/null` - 进入隔离模式，禁用所有工具，变成纯问答
- `/notnull` - 退出隔离模式，恢复正常工具调用

## 行为描述

### `/null` (进入隔离)
- 所有工具（Read/Write/Edit/Bash/Glob/Grep等）全部禁用
- 直接将用户后续输入透传给 LLM，返回回复
- 不加载任何项目上下文（CLAUDE.md、tools/ 等）
- 适合简单问答、计算、翻译等轻量任务

### `/notnull` (退出隔离)
- 恢复所有工具调用
- 重新加载项目上下文
- 回到正常 Agent 模式

## 实现细节

### 状态管理
在 `Session` 或 `CLI` 中新增 `isolatedMode: boolean` 状态

### 工具禁用逻辑
```java
if (session.isolatedMode && toolCallRequested) {
    return "工具在隔离模式下不可用。请输入 /notnull 退出隔离模式。";
}
```

### 命令拦截
在解析用户输入时，优先检查是否为内置斜杠命令（`/null`, `/notnull`, `/help` 等）

## 验收标准
1. 输入 `/null` 后，工具调用返回禁用提示
2. 输入 `/notnull` 后，工具恢复正常
3. 隔离模式下 LLM 仍然正常工作（纯对话）
4. 状态切换无副作用
