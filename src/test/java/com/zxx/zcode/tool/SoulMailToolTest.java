package com.zxx.zcode.tool;

import com.zxx.zcode.soul.SoulProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SoulMailToolTest {

    @Test
    void sendAndReceiveBetweenSouls(@TempDir Path workDir) throws Exception {
        SoulMailTool fromA = new SoulMailTool(workDir, new SoulProfile("alice", "Alice", "", java.util.List.of()));
        SoulMailTool fromB = new SoulMailTool(workDir, new SoulProfile("bob", "Bob", "", java.util.List.of()));

        String sendR = fromA.execute(Map.of(
                "action", "send",
                "to_soul", "bob",
                "message", "Please review Foo.java"));
        assertTrue(sendR.contains("bob"));

        String recv = fromB.execute(Map.of("action", "receive", "limit", 5, "consume", false));
        assertTrue(recv.contains("Foo.java"));
        assertTrue(recv.contains("alice"));

        String recvConsumed = fromB.execute(Map.of("action", "receive", "limit", 5, "consume", true));
        assertTrue(recvConsumed.contains("Foo.java"));

        String empty = fromB.execute(Map.of("action", "receive"));
        assertTrue(empty.contains("no messages"));
    }

    @Test
    void skillSuggestKindPrefixesMessage(@TempDir Path workDir) {
        SoulMailTool fromGg = new SoulMailTool(workDir, new SoulProfile("gg", "GG", "", java.util.List.of()));
        SoulMailTool fromCc = new SoulMailTool(workDir, new SoulProfile("cc", "CC", "", java.util.List.of()));

        String sendR = fromGg.execute(Map.of(
                "action", "send",
                "to_soul", "cc",
                "kind", "skill_suggest",
                "message", "Please run skill task-breakdown for feature X"));
        assertTrue(sendR.contains("cc"));

        String recv = fromCc.execute(Map.of("action", "receive", "limit", 5, "consume", true));
        assertTrue(recv.contains("[SKILL_SUGGEST]"), recv);
        assertTrue(recv.contains("task-breakdown"));
    }

    @Test
    void cannotSendToSelf() {
        SoulMailTool t = new SoulMailTool(Path.of("/tmp"), new SoulProfile("x", "X", "", java.util.List.of()));
        String r = t.execute(Map.of("action", "send", "to_soul", "x", "message", "hi"));
        assertTrue(r.contains("Error"));
    }
}
