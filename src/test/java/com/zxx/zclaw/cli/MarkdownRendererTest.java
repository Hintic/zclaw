package com.zxx.zclaw.cli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MarkdownRendererTest {

    private final MarkdownRenderer r = new MarkdownRenderer();

    @Test
    void boldDoubleAsterisksBecomesAnsiBold() {
        String out = r.render("prefix **cc** suffix");
        assertTrue(out.contains("cc"), "inner text preserved");
        assertFalse(out.contains("**"), "asterisks stripped");
        assertTrue(out.contains("\u001B[1m"), "bold SGR present");
        assertTrue(out.contains("\u001B[0m"), "reset present");
    }

    @Test
    void boldUnderscoreSupported() {
        String out = r.render("__x__");
        assertTrue(out.contains("\u001B[1m"));
        assertFalse(out.contains("__"));
    }
}
