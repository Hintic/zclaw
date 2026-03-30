package com.zxx.zclaw.cli;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ToolResultFormatterTest {

    private ToolResultFormatter formatter;

    @BeforeEach
    void setUp() {
        formatter = new ToolResultFormatter();
    }

    // Bash tests
    @Test
    void formatBash_shortOutput_showsAllLines() {
        String result = "line1\nline2\nline3";
        String formatted = formatter.format("bash", "{}", result);
        assertTrue(formatted.contains("3 lines"));
        assertTrue(formatted.contains("line1"));
        assertTrue(formatted.contains("line2"));
        assertTrue(formatted.contains("line3"));
    }

    @Test
    void formatBash_longOutput_truncates() {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 30; i++) {
            sb.append("line").append(i).append("\n");
        }
        String result = sb.toString();
        String formatted = formatter.format("bash", "{}", result);
        assertTrue(formatted.contains("30 lines"));
        assertTrue(formatted.contains("line1"));
        assertTrue(formatted.contains("more lines"));
        assertTrue(formatted.contains("line26")); // last 5 lines should include line26
    }

    @Test
    void formatBash_emptyOutput() {
        String formatted = formatter.format("bash", "{}", "");
        assertTrue(formatted.contains("(no output)"));
    }

    @Test
    void formatBash_errorExitCode() {
        String result = "some output\n[Exit code: 1]";
        String formatted = formatter.format("bash", "{}", result);
        assertTrue(formatted.contains("[Error]"));
    }

    // ReadFile tests
    @Test
    void formatReadFile_shortFile_showsAllLines() {
        String result = "     1\tpackage com.example;\n     2\tpublic class Example {}";
        String formatted = formatter.format("read_file", "{\"file_path\":\"Example.java\"}", result);
        assertTrue(formatted.contains("2 lines"));
        assertTrue(formatted.contains("package com.example"));
    }

    @Test
    void formatReadFile_longFile_truncates() {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 60; i++) {
            sb.append(String.format("%6d\tline %d\n", i, i));
        }
        String result = sb.toString();
        String formatted = formatter.format("read_file", "{\"file_path\":\"Example.java\"}", result);
        assertTrue(formatted.contains("60 lines"));
        assertTrue(formatted.contains("more lines hidden"));
        assertTrue(formatted.contains("line 1"));
        assertTrue(formatted.contains("line 51")); // last 10 lines
    }

    // WriteFile tests
    @Test
    void formatWriteFile_success() {
        String result = "File written successfully: /path/to/file.java (1234 chars)";
        String formatted = formatter.format("write_file", "{}", result);
        assertTrue(formatted.contains("✓"));
        assertTrue(formatted.contains("File written successfully"));
        assertTrue(formatted.contains("/path/to/file.java"));
        assertTrue(formatted.contains("1234 chars"));
    }

    // EditFile tests
    @Test
    void formatEditFile_success() {
        String result = "File edited: /path/to/Example.java\n\n--- a/path/to/Example.java\n+++ b/path/to/Example.java\n-old text\n+new text\n+with more\n+lines";
        String args = "{}";
        String formatted = formatter.format("edit_file", args, result);
        assertTrue(formatted.contains("✓"));
        assertTrue(formatted.contains("/path/to/Example.java"));
        assertTrue(formatted.contains("--- a/"));
        assertTrue(formatted.contains("+new text"));
    }

    @Test
    void formatEditFile_error() {
        String result = "Error: old_string not found in file";
        String formatted = formatter.format("edit_file", "{}", result);
        assertTrue(formatted.contains("✗"));
    }

    // Glob tests
    @Test
    void formatGlob_fewFiles() {
        String result = "3 files matched:\nfile1.java\nfile2.java\nfile3.java";
        String formatted = formatter.format("glob", "{}", result);
        assertTrue(formatted.contains("3 files matched"));
        assertTrue(formatted.contains("file1.java"));
    }

    @Test
    void formatGlob_manyFiles_truncates() {
        StringBuilder sb = new StringBuilder("20 files matched:\n");
        for (int i = 1; i <= 20; i++) {
            sb.append("file").append(i).append(".java\n");
        }
        String result = sb.toString();
        String formatted = formatter.format("glob", "{}", result);
        assertTrue(formatted.contains("20 files matched"));
        assertTrue(formatted.contains("more files"));
    }

    @Test
    void formatGlob_noMatches() {
        String result = "No files matched pattern: **/*.xyz";
        String formatted = formatter.format("glob", "{}", result);
        assertTrue(formatted.contains("No files matched"));
    }

    // Grep tests
    @Test
    void formatGrep_fewMatches() {
        String result = "file1.java:3: public class A\nfile2.java:10: public class B";
        String formatted = formatter.format("grep", "{}", result);
        assertTrue(formatted.contains("2 matches"));
        assertTrue(formatted.contains("file1.java:3"));
    }

    @Test
    void formatGrep_manyMatches_truncates() {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 40; i++) {
            sb.append("file").append(i).append(".java:").append(i).append(": match\n");
        }
        String result = sb.toString();
        String formatted = formatter.format("grep", "{}", result);
        assertTrue(formatted.contains("40 matches"));
        assertTrue(formatted.contains("more hidden"));
    }

    @Test
    void formatGrep_noMatches() {
        String result = "No matches found for pattern: nonexistent";
        String formatted = formatter.format("grep", "{}", result);
        assertTrue(formatted.contains("No matches found"));
    }

    // Default format tests
    @Test
    void formatDefault_unknownTool() {
        String result = "some result";
        String formatted = formatter.format("unknown_tool", "{}", result);
        assertTrue(formatted.contains("some result"));
    }

    @Test
    void formatDefault_longResult_truncates() {
        String result = "x".repeat(600);
        String formatted = formatter.format("unknown_tool", "{}", result);
        assertTrue(formatted.contains("more chars"));
    }

    @Test
    void formatTaskPlan_showsBoard() {
        String result = """
                ZCLAW_TASK_PLAN_V1
                goal: Ship feature
                step: 1 | status=done | title=Design
                step: 2 | status=in_progress | title=Code | note=wip
                ZCLAW_TASK_PLAN_END

                ## Task breakdown
                """;

        String formatted = formatter.format("task_plan", "{}", result);
        assertTrue(formatted.contains("TASK BOARD"));
        assertTrue(formatted.contains("Ship feature"));
        assertTrue(formatted.contains("Design"));
        assertTrue(formatted.contains("Code"));
    }
}
