package com.zxx.zcode.llm.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LLMResponseTest {

    @Test
    void testHasToolCalls_empty() {
        LLMResponse resp = new LLMResponse();
        assertFalse(resp.hasToolCalls());
    }

    @Test
    void testHasToolCalls_withCalls() {
        LLMResponse resp = new LLMResponse();
        resp.setToolCalls(List.of(new ToolCallInfo("id1", "read_file", "{}")));
        assertTrue(resp.hasToolCalls());
    }

    @Test
    void testHasWebSearchResults_empty() {
        LLMResponse resp = new LLMResponse();
        assertFalse(resp.hasWebSearchResults());
    }

    @Test
    void testHasWebSearchResults_withResults() {
        LLMResponse resp = new LLMResponse();
        resp.setWebSearchResults(List.of(
                new WebSearchResult("query", "https://example.com", "Example", "1 day ago")));
        assertTrue(resp.hasWebSearchResults());
    }

    @Test
    void testGetTextContent() {
        LLMResponse resp = new LLMResponse();
        assertNull(resp.getTextContent());

        resp.setTextContent("Hello world");
        assertEquals("Hello world", resp.getTextContent());
    }

    @Test
    void testTokenUsage() {
        LLMResponse resp = new LLMResponse();
        resp.setInputTokens(100);
        resp.setOutputTokens(50);
        assertEquals(100, resp.getInputTokens());
        assertEquals(50, resp.getOutputTokens());
    }

    @Test
    void testRawAssistantMessage() {
        LLMResponse resp = new LLMResponse();
        Message msg = Message.assistant("hello", null);
        resp.setRawAssistantMessage(msg);
        assertSame(msg, resp.getRawAssistantMessage());
    }
}
