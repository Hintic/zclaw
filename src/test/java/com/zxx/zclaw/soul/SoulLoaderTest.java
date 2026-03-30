package com.zxx.zclaw.soul;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@Execution(ExecutionMode.SAME_THREAD)
class SoulLoaderTest {

    private void withFakeHome(Path home, Runnable body) {
        String prev = System.getProperty("user.home");
        System.setProperty("user.home", home.toString());
        try {
            body.run();
        } finally {
            if (prev != null) {
                System.setProperty("user.home", prev);
            } else {
                System.clearProperty("user.home");
            }
        }
    }

    @Test
    void workDirOverridesUserHomeSoulsDir() throws Exception {
        Path root = Files.createTempDirectory("zclaw-soul-test");
        Path fakeHome = root.resolve("home");
        Path work = root.resolve("work");
        Files.createDirectories(fakeHome.resolve(".zclaw/souls"));
        Files.createDirectories(work.resolve(".zclaw/souls"));
        Files.writeString(fakeHome.resolve(".zclaw/souls/demo.json"), """
                {"id":"demo","display_name":"FromHome","persona":"home persona","peers":[]}
                """);
        Files.writeString(work.resolve(".zclaw/souls/demo.json"), """
                {"id":"demo","display_name":"FromWork","persona":"work persona","peers":["p"]}
                """);

        withFakeHome(fakeHome, () -> {
            SoulProfile p = SoulLoader.load("demo", work);
            assertEquals("demo", p.getId());
            assertEquals("FromWork", p.getDisplayName());
            assertEquals("work persona", p.getPersona());
            assertEquals("p", p.getPeers().getFirst());
        });
    }

    @Test
    void fallsBackToUserHomeWhenWorkDirHasNoSoulFile() throws Exception {
        Path root = Files.createTempDirectory("zclaw-soul-test2");
        Path fakeHome = root.resolve("h");
        Path work = root.resolve("w");
        Files.createDirectories(fakeHome.resolve(".zclaw/souls"));
        Files.createDirectories(work.resolve(".zclaw/souls"));
        Files.writeString(fakeHome.resolve(".zclaw/souls/onlyhome.json"), """
                {"id":"onlyhome","display_name":"H","persona":"x","peers":[]}
                """);

        withFakeHome(fakeHome, () -> {
            SoulProfile p = SoulLoader.load("onlyhome", work);
            assertEquals("onlyhome", p.getId());
            assertEquals("H", p.getDisplayName());
            assertEquals("x", p.getPersona());
        });
    }

    @Test
    void nestedSoulJsonPreferredOverFlatInWorkDir() throws Exception {
        Path root = Files.createTempDirectory("zclaw-soul-nested");
        Path work = root.resolve("work");
        Files.createDirectories(work.resolve(".zclaw/souls/demo"));
        Files.writeString(work.resolve(".zclaw/souls/demo/soul.json"), """
                {"id":"demo","display_name":"FromNested","persona":"nested","peers":[]}
                """);
        Files.writeString(work.resolve(".zclaw/souls/demo.json"), """
                {"id":"demo","display_name":"FromFlat","persona":"flat","peers":[]}
                """);

        SoulProfile p = SoulLoader.load("demo", work);
        assertEquals("demo", p.getId());
        assertEquals("FromNested", p.getDisplayName());
        assertEquals("nested", p.getPersona());
    }
}
