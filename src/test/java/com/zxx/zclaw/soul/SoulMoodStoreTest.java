package com.zxx.zclaw.soul;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SoulMoodStoreTest {

    @Test
    void clampAndEvents(@TempDir Path workDir) throws Exception {
        SoulMoodStore store = new SoulMoodStore(workDir);
        SoulMoodStore.ApplyResult r1 = store.applyEvent("gg", SoulMoodStore.MoodEvent.USER_PRAISE);
        assertTrue(r1.state.score > 72);
        SoulMoodStore.ApplyResult r2 = store.applyEvent("gg", SoulMoodStore.MoodEvent.USER_CRITICISM);
        assertTrue(r2.state.score < r1.state.score);
    }

    @Test
    void calmBreakCooldown(@TempDir Path workDir) throws Exception {
        SoulMoodStore store = new SoulMoodStore(workDir);
        SoulMoodStore.ApplyResult a = store.applyEvent("cc", SoulMoodStore.MoodEvent.CALM_BREAK);
        assertTrue(a.message.contains("CALM_BREAK"));
        SoulMoodStore.ApplyResult b = store.applyEvent("cc", SoulMoodStore.MoodEvent.CALM_BREAK);
        assertTrue(b.message.contains("cooldown") || b.message.contains("try again"));
    }

    @Test
    void ggCcWorkflowDetects() {
        assertTrue(SoulMoodStore.isGgCcSetup(new SoulProfile("gg", "G", "", java.util.List.of())));
        assertFalse(SoulMoodStore.isGgCcSetup(SoulProfile.defaultProfile()));
        assertTrue(SoulMoodStore.isGgCcSetup(new SoulProfile("other", "O", "", java.util.List.of("gg"))));
    }

    @Test
    void autoRecoverFivePerMinuteUntil72(@TempDir Path workDir) throws Exception {
        SoulMoodStore store = new SoulMoodStore(workDir);
        long now = System.currentTimeMillis();
        SoulMoodStore.State st = new SoulMoodStore.State(57, 0L, now - 2 * 60_000L);
        store.save("gg", st);

        SoulMoodStore.State loaded = store.load("gg");
        assertEquals(67, loaded.score);
    }

    @Test
    void autoRecoverCapsAt72(@TempDir Path workDir) throws Exception {
        SoulMoodStore store = new SoulMoodStore(workDir);
        long now = System.currentTimeMillis();
        SoulMoodStore.State st = new SoulMoodStore.State(69, 0L, now - 3 * 60_000L);
        store.save("cc", st);

        SoulMoodStore.State loaded = store.load("cc");
        assertEquals(72, loaded.score);
    }

    @Test
    void autoDecayFivePerMinuteFromAbove(@TempDir Path workDir) throws Exception {
        SoulMoodStore store = new SoulMoodStore(workDir);
        long now = System.currentTimeMillis();
        SoulMoodStore.State st = new SoulMoodStore.State(87, 0L, now - 2 * 60_000L);
        store.save("hi", st);

        SoulMoodStore.State loaded = store.load("hi");
        assertEquals(77, loaded.score);
    }

    @Test
    void autoDecayFloorsAt72(@TempDir Path workDir) throws Exception {
        SoulMoodStore store = new SoulMoodStore(workDir);
        long now = System.currentTimeMillis();
        SoulMoodStore.State st = new SoulMoodStore.State(95, 0L, now - 10 * 60_000L);
        store.save("hi2", st);

        SoulMoodStore.State loaded = store.load("hi2");
        assertEquals(72, loaded.score);
    }
}
