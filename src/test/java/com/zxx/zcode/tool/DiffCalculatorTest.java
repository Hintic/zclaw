package com.zxx.zcode.tool;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DiffCalculatorTest {

    private final DiffCalculator diffCalculatorNoColor = new DiffCalculator(false);

    @Test
    void generateUnifiedDiff_noChanges_returnsEmpty() {
        String content = "line1\nline2\nline3";
        String diff = diffCalculatorNoColor.generateUnifiedDiff(content, content, "test.txt");
        assertEquals("", diff);
    }

    @Test
    void generateUnifiedDiff_singleLineChange() {
        String oldContent = "line1\nline2\nline3";
        String newContent = "line1\nmodified\nline3";

        String diff = diffCalculatorNoColor.generateUnifiedDiff(oldContent, newContent, "test.txt");

        assertTrue(diff.contains("--- a/test.txt"));
        assertTrue(diff.contains("+++ b/test.txt"));
        assertTrue(diff.contains("-line2"));
        assertTrue(diff.contains("+modified"));
    }

    @Test
    void generateUnifiedDiff_addLine() {
        String oldContent = "line1\nline2";
        String newContent = "line1\nline2\nline3";

        String diff = diffCalculatorNoColor.generateUnifiedDiff(oldContent, newContent, "test.txt");

        assertTrue(diff.contains("+line3"));
    }

    @Test
    void generateUnifiedDiff_deleteLine() {
        String oldContent = "line1\nline2\nline3";
        String newContent = "line1\nline3";

        String diff = diffCalculatorNoColor.generateUnifiedDiff(oldContent, newContent, "test.txt");

        assertTrue(diff.contains("-line2"));
    }

    @Test
    void generateUnifiedDiff_multipleChanges() {
        String oldContent = "a\nb\nc\nd\ne";
        String newContent = "a\nX\nc\nY\ne";

        String diff = diffCalculatorNoColor.generateUnifiedDiff(oldContent, newContent, "test.txt");

        assertTrue(diff.contains("-b"));
        assertTrue(diff.contains("+X"));
        assertTrue(diff.contains("-d"));
        assertTrue(diff.contains("+Y"));
    }

    @Test
    void generateUnifiedDiff_replaceAll() {
        String oldContent = "foo\nfoo\nfoo";
        String newContent = "bar\nbar\nbar";

        String diff = diffCalculatorNoColor.generateUnifiedDiff(oldContent, newContent, "test.txt");

        assertTrue(diff.contains("-foo"));
        assertTrue(diff.contains("+bar"));
        int deleteCount = countOccurrences(diff, "-foo");
        int addCount = countOccurrences(diff, "+bar");
        assertEquals(3, deleteCount);
        assertEquals(3, addCount);
    }

    @Test
    void generateUnifiedDiff_emptyToContent() {
        String oldContent = "";
        String newContent = "new content";

        String diff = diffCalculatorNoColor.generateUnifiedDiff(oldContent, newContent, "test.txt");

        assertTrue(diff.contains("+new content"));
    }

    @Test
    void generateUnifiedDiff_contentToEmpty() {
        String oldContent = "old content";
        String newContent = "";

        String diff = diffCalculatorNoColor.generateUnifiedDiff(oldContent, newContent, "test.txt");

        assertTrue(diff.contains("-old content"));
    }

    @Test
    void generateUnifiedDiff_hasHeader() {
        String oldContent = "line1";
        String newContent = "line2";

        String diff = diffCalculatorNoColor.generateUnifiedDiff(oldContent, newContent, "path/to/file.java");

        assertTrue(diff.startsWith("--- a/path/to/file.java"));
        assertTrue(diff.contains("+++ b/path/to/file.java"));
    }

    @Test
    void generateUnifiedDiff_withColor_enabled() {
        DiffCalculator diffCalculatorWithColor = new DiffCalculator(true);
        String oldContent = "line1\nline2";
        String newContent = "line1\nmodified";

        String diff = diffCalculatorWithColor.generateUnifiedDiff(oldContent, newContent, "test.txt");

        assertTrue(diff.contains("\u001b[41m")); // RED_BG
        assertTrue(diff.contains("\u001b[42m")); // GREEN_BG
        assertTrue(diff.contains("\u001b[0m"));  // RESET
    }

    private int countOccurrences(String str, String sub) {
        int count = 0;
        int idx = 0;
        while ((idx = str.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }
}
