package com.zxx.zclaw.llm.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MessageTest {

    @Test
    void testSystemMessage() {
        Message msg = Message.system("You are helpful.");
        assertEquals("system", msg.getRole());
        assertEquals("You are helpful.", msg.getContent());
        assertEquals("You are helpful.", msg.getContentAsString());
    }

    @Test
    void testUserMessage() {
        Message msg = Message.user("Hello");
        assertEquals("user", msg.getRole());
        assertEquals("Hello", msg.getContentAsString());
    }

    @Test
    void testAssistantMessage_textOnly() {
        Message msg = Message.assistant("Hi there", null);
        assertEquals("assistant", msg.getRole());
        assertEquals("Hi there", msg.getContentAsString());
        assertFalse(msg.hasToolCalls());
        assertNull(msg.getAnthropicContent());
    }

    @Test
    void testAssistantMessage_withToolCalls() {
        ToolCall tc = new ToolCall();
        Message msg = Message.assistant(null, List.of(tc));
        assertEquals("assistant", msg.getRole());
        assertTrue(msg.hasToolCalls());
        assertEquals(1, msg.getToolCalls().size());
    }

    @Test
    void testToolResultMessage_openai() {
        Message msg = Message.toolResult("call_123", "file contents");
        assertEquals("tool", msg.getRole());
        assertEquals("call_123", msg.getToolCallId());
        assertEquals("file contents", msg.getContentAsString());
    }

    @Test
    void testAnthropicAssistantMessage() {
        ContentBlock textBlock = new ContentBlock();
        textBlock.setType("text");
        textBlock.setText("Hello from search");

        ContentBlock toolBlock = new ContentBlock();
        toolBlock.setType("server_tool_use");
        toolBlock.setName("web_search");

        List<ContentBlock> blocks = List.of(textBlock, toolBlock);
        Message msg = Message.anthropicAssistant(blocks);

        assertEquals("assistant", msg.getRole());
        assertNotNull(msg.getAnthropicContent());
        assertEquals(2, msg.getAnthropicContent().size());
        assertEquals("Hello from search", msg.getContentAsString());
    }

    @Test
    void testAnthropicAssistantMessage_noText() {
        ContentBlock toolBlock = new ContentBlock();
        toolBlock.setType("tool_use");
        toolBlock.setId("toolu_123");
        toolBlock.setName("read_file");

        Message msg = Message.anthropicAssistant(List.of(toolBlock));
        assertEquals("assistant", msg.getRole());
        assertNull(msg.getContentAsString());
        assertNotNull(msg.getAnthropicContent());
    }

    @Test
    void testAnthropicToolResultMessage() {
        Map<String, Object> result = Map.of(
                "type", "tool_result",
                "tool_use_id", "toolu_123",
                "content", "file data");
        Message msg = Message.anthropicToolResult(List.of(result));
        assertEquals("user", msg.getRole());
        assertNotNull(msg.getContent());
        assertTrue(msg.getContent() instanceof List);
    }

    @Test
    void testToString() {
        Message msg = Message.user("Hello world test message that is longer than fifty characters for testing");
        String str = msg.toString();
        assertTrue(str.contains("user"));
        assertTrue(str.contains("Hello world"));
    }
}
