package com.zxx.zclaw.cli;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.*;

class ThinkingIndicatorTest {

    @Test
    void testStartAndStop() throws InterruptedException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos);
        ThinkingIndicator indicator = new ThinkingIndicator(ps);

        indicator.start();
        Thread.sleep(200); // Let spinner run a few frames
        indicator.stop();

        String output = baos.toString();
        assertTrue(output.contains("Thinking..."), "Should contain 'Thinking...' text");
    }

    @Test
    void testStopClearsLine() throws InterruptedException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos);
        ThinkingIndicator indicator = new ThinkingIndicator(ps);

        indicator.start();
        Thread.sleep(150);
        indicator.stop();

        String output = baos.toString();
        // After stop: CR + EL (erase in line) per ThinkingIndicator.stop()
        assertTrue(output.endsWith("\r\u001B[2K"), "Output should end with erase-line sequence after clearing");
    }

    @Test
    void testMultipleStartStop() throws InterruptedException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos);
        ThinkingIndicator indicator = new ThinkingIndicator(ps);

        // First cycle
        indicator.start();
        Thread.sleep(100);
        indicator.stop();

        // Second cycle
        indicator.start();
        Thread.sleep(100);
        indicator.stop();

        String output = baos.toString();
        // Should contain Thinking... from both cycles
        int count = countOccurrences(output, "Thinking...");
        assertTrue(count >= 2, "Should show Thinking... in both cycles, got " + count);
    }

    @Test
    void testDoubleStartIsNoOp() throws InterruptedException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos);
        ThinkingIndicator indicator = new ThinkingIndicator(ps);

        indicator.start();
        indicator.start(); // Should be ignored
        Thread.sleep(100);
        indicator.stop();

        // Should not throw and output should be normal
        String output = baos.toString();
        assertTrue(output.contains("Thinking..."));
    }

    @Test
    void testDoubleStopIsNoOp() throws InterruptedException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos);
        ThinkingIndicator indicator = new ThinkingIndicator(ps);

        indicator.start();
        Thread.sleep(100);
        indicator.stop();
        indicator.stop(); // Should be ignored, no exception

        String output = baos.toString();
        assertTrue(output.contains("Thinking..."));
    }

    @Test
    void testStopWithoutStartIsNoOp() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos);
        ThinkingIndicator indicator = new ThinkingIndicator(ps);

        // Should not throw
        indicator.stop();

        assertEquals("", baos.toString());
    }

    @Test
    void testImmediateStop() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos);
        ThinkingIndicator indicator = new ThinkingIndicator(ps);

        indicator.start();
        indicator.stop(); // Stop immediately

        // Should not hang or throw
        String output = baos.toString();
        assertNotNull(output);
    }

    @Test
    void testCustomStatusLine() throws InterruptedException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos);
        ThinkingIndicator indicator = new ThinkingIndicator(ps);

        indicator.start("bash running...");
        Thread.sleep(200);
        indicator.stop();

        String output = baos.toString();
        assertTrue(output.contains("bash running..."), "Should show custom tool status");
    }

    @Test
    void testSanitizeLabelTruncatesAndStripsControls() {
        String longName = "x".repeat(60);
        String s = ThinkingIndicator.sanitizeLabel(longName + " running...");
        assertTrue(s.length() <= 52, "Label should be truncated");
        assertFalse(s.contains("\r"));
        assertFalse(s.contains("\n"));
    }

    @Test
    void testSanitizeLabelRespectsNarrowTerminal() {
        String s = ThinkingIndicator.sanitizeLabel("x".repeat(40) + " running...", 32);
        assertTrue(s.length() <= 27, "Narrow terminal should cap label so spinner stays on one row");
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
