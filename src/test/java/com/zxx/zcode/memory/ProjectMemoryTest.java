package com.zxx.zcode.memory;

import com.zxx.zcode.soul.SoulProfile;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectMemoryTest {

    @Test
    void loadMissingReturnsEmpty() throws Exception {
        Path dir = Files.createTempDirectory("pm1");
        assertEquals("", ProjectMemory.load(dir, SoulProfile.defaultProfile()));
    }

    @Test
    void roundTripDefaultSoulUsesSoulsDefaultWhenNoRootFile() throws Exception {
        Path dir = Files.createTempDirectory("pm2");
        ProjectMemory.save(dir, SoulProfile.defaultProfile(), "## T\n- a\n");
        assertEquals("## T\n- a", ProjectMemory.load(dir, SoulProfile.defaultProfile()));
        Path expected = dir.resolve(".zcode/souls/default/memory.md");
        assertTrue(Files.isRegularFile(expected));
        assertEquals(expected, ProjectMemory.path(dir, SoulProfile.defaultProfile()));
    }

    @Test
    void namedSoulMemoryUnderSoulHome() throws Exception {
        Path dir = Files.createTempDirectory("pm3");
        SoulProfile cc = new SoulProfile("cc", "C", "", java.util.List.of());
        ProjectMemory.save(dir, cc, "x\n");
        Path expected = dir.resolve(".zcode/souls/cc/memory.md");
        assertTrue(Files.isRegularFile(expected));
        assertEquals("x", ProjectMemory.load(dir, cc));
    }
}
