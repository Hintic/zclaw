package com.zxx.zcode.memory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MemorySummarizerTest {

    @Test
    void stripOuterCodeFence_removesFence() {
        assertEquals("hello", MemorySummarizer.stripOuterCodeFence("```markdown\nhello\n```"));
        assertEquals("x", MemorySummarizer.stripOuterCodeFence("```\nx\n```"));
    }

    @Test
    void stripOuterCodeFence_noFence() {
        assertEquals("plain", MemorySummarizer.stripOuterCodeFence("plain"));
    }
}
