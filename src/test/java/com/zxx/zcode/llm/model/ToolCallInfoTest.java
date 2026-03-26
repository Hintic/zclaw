package com.zxx.zcode.llm.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ToolCallInfoTest {

    @Test
    void testGetters() {
        ToolCallInfo info = new ToolCallInfo("id_1", "read_file", "{\"file_path\": \"test.txt\"}");
        assertEquals("id_1", info.getId());
        assertEquals("read_file", info.getName());
        assertEquals("{\"file_path\": \"test.txt\"}", info.getArgumentsJson());
    }

    @Test
    void testToString() {
        ToolCallInfo info = new ToolCallInfo("id_1", "bash", "{}");
        String str = info.toString();
        assertTrue(str.contains("id_1"));
        assertTrue(str.contains("bash"));
    }
}
