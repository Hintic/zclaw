package com.zxx.zclaw.tool;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;

import static org.junit.jupiter.api.Assertions.*;

class EditFileToolDiffTest {

    @TempDir
    Path tempDir;

    @Test
    void editFile_showsDiff() throws Exception {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "line1\nline2\nline3\nline4\nline5");

        DiffCalculator diffCalculator = new DiffCalculator(false);
        EditFileTool tool = new EditFileTool(tempDir, diffCalculator);

        java.util.Map<String, Object> args = new java.util.HashMap<>();
        args.put("file_path", "test.txt");
        args.put("old_string", "line2");
        args.put("new_string", "modified_line2");

        String result = tool.execute(args);

        System.out.println("=== Result ===");
        System.out.println(result);
        System.out.println("==============");

        assertTrue(result.contains("File edited:"));
        assertTrue(result.contains("--- a/"));
        assertTrue(result.contains("+++ b/"));
        assertTrue(result.contains("-line2"));
        assertTrue(result.contains("+modified_line2"));
    }

    @Test
    void editFile_multipleChanges() throws Exception {
        Path file = tempDir.resolve("multi.txt");
        Files.writeString(file, "foo\nfoo\nfoo\nbar\nbar");

        DiffCalculator diffCalculator = new DiffCalculator(false);
        EditFileTool tool = new EditFileTool(tempDir, diffCalculator);

        java.util.Map<String, Object> args = new java.util.HashMap<>();
        args.put("file_path", "multi.txt");
        args.put("old_string", "foo");
        args.put("new_string", "baz");
        args.put("replace_all", true);

        String result = tool.execute(args);

        System.out.println("=== Multi Change Result ===");
        System.out.println(result);
        System.out.println("===========================");

        assertTrue(result.contains("-foo"));
        assertTrue(result.contains("+baz"));
        assertTrue(result.contains("+baz"));
    }

    @Test
    void editFile_withColor() throws Exception {
        Path file = tempDir.resolve("color.txt");
        Files.writeString(file, "old\ncontent");

        DiffCalculator diffCalculator = new DiffCalculator(true);
        EditFileTool tool = new EditFileTool(tempDir, diffCalculator);

        java.util.Map<String, Object> args = new java.util.HashMap<>();
        args.put("file_path", "color.txt");
        args.put("old_string", "old");
        args.put("new_string", "new");

        String result = tool.execute(args);

        System.out.println("=== Color Result ===");
        System.out.println(result);
        System.out.println("=====================");

        assertTrue(result.contains("\u001b[41m")); // RED_BG for deleted
        assertTrue(result.contains("\u001b[42m")); // GREEN_BG for added
    }
}
