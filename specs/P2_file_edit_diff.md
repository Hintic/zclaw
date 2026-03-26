# P2: 文件编辑 Diff 展示

## 1. 功能设计

### 目标
在 `EditFileTool` 执行文件编辑后，展示 unified diff 格式的变更摘要，使用 ANSI 颜色：
- 红色背景/白色文字：删除的行
- 绿色背景/黑色文字：新增的行

### 输出格式
```
File edited: src/main/java/Example.java

--- a/src/main/java/Example.java
+++ b/src/main/java/Example.java
@@ -5,7 +5,7 @@ public class Example {
-    private String oldField;
+    private String newField;
     public void method() {
-        oldCode();
+        newCode();
     }
```

## 2. 实现细节

### 核心类
- `DiffCalculator`：计算两段文本的 unified diff
- 修改 `EditFileTool.execute()`：在写入文件后生成并返回 diff

### DiffCalculator 实现
```java
public class DiffCalculator {
    public static String generateUnifiedDiff(String oldContent, String newContent, String filePath);
}
```

使用简单的行对比算法：
1. 按行分割 oldContent 和 newContent
2. 使用 LCS (Longest Common Subsequence) 找出差异
3. 生成 unified diff 格式

### ANSI 颜色
- 删除：`\u001b[41m` (红背景) + `\u001b[97m` (白字)
- 新增：`\u001b[42m` (绿背景) + `\u001b[30m` (黑字)
- 重置：`\u001b[0m`

### 颜色感知输出
- 检测终端是否支持颜色（检查 isTTY 或 `NO_COLOR` 环境变量）
- 颜色关闭时只输出纯文本 diff

## 3. 验收标准

1. ✅ 文件编辑后显示 unified diff
2. ✅ 删除行显示红底白字，新增行显示绿底黑字
3. ✅ 文件不存在时不显示 diff
4. ✅ 空修改（无变化）时不显示 diff
5. ✅ 大文件（>100行）diff 正确截断（显示前后各 3 行 context）
6. ✅ 支持 `replace_all` 模式的多处修改
7. ✅ 单元测试覆盖 DiffCalculator
