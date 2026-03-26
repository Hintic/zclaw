package com.zxx.zcode.llm.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WebSearchResultTest {

    @Test
    void testGetters() {
        WebSearchResult r = new WebSearchResult(
                "java agent framework", "https://github.com/langchain4j", "langchain4j", "2 days ago");
        assertEquals("java agent framework", r.getQuery());
        assertEquals("https://github.com/langchain4j", r.getUrl());
        assertEquals("langchain4j", r.getTitle());
        assertEquals("2 days ago", r.getPageAge());
    }

    @Test
    void testToString() {
        WebSearchResult r = new WebSearchResult("q", "https://x.com", "Title", null);
        String str = r.toString();
        assertTrue(str.contains("Title"));
        assertTrue(str.contains("https://x.com"));
    }
}
