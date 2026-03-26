package com.zxx.zcode.cli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StreamThinkingStripperTest {

    @Test
    void oneChunk_backtickPair() {
        StreamThinkingStripper s = new StreamThinkingStripper();
        assertEquals("abcd", s.feed("ab`think`hidden`/think`cd"));
        assertEquals("", s.drain());
    }

    @Test
    void splitOpen_backtick() {
        StreamThinkingStripper s = new StreamThinkingStripper();
        assertEquals("x", s.feed("x`thi"));
        assertEquals("z", s.feed("nk`y`/think`z"));
        assertEquals("", s.drain());
    }

    @Test
    void xmlPair_oneChunk() {
        StreamThinkingStripper s = new StreamThinkingStripper();
        assertEquals("QX", s.feed("Q\u003cthink\u003ehidden\u003c/think\u003eX"));
        assertEquals("", s.drain());
    }

    @Test
    void drain_flushesTailWhenNoClose() {
        StreamThinkingStripper s = new StreamThinkingStripper();
        assertEquals("", s.feed("`think`still going"));
        // Open was consumed into thinking mode; no close — tail is flushed (not discarded).
        assertEquals("still going", s.drain());
    }

    @Test
    void backtickOpen_xmlClose_stripsBlock() {
        StreamThinkingStripper s = new StreamThinkingStripper();
        assertEquals("hi", s.feed("`think`reasoning\u003c/think\u003ehi"));
        assertEquals("", s.drain());
    }
}
