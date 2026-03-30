package com.zxx.zclaw.skill;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Execution(ExecutionMode.SAME_THREAD)
class SkillLoaderTest {

    private String previousUserHome;

    @TempDir
    private Path tempRoot;

    private Path workDir;
    private Path fakeHome;

    @BeforeEach
    void isolateUserHome() throws Exception {
        previousUserHome = System.getProperty("user.home");
        fakeHome = tempRoot.resolve("home");
        workDir = tempRoot.resolve("work");
        Files.createDirectories(fakeHome);
        System.setProperty("user.home", fakeHome.toString());
    }

    @AfterEach
    void restoreUserHome() {
        if (previousUserHome != null) {
            System.setProperty("user.home", previousUserHome);
        } else {
            System.clearProperty("user.home");
        }
    }

    private static Path soulSkillPack(Path workDir, String soulId, String packId) {
        return workDir.resolve(".zclaw/souls").resolve(soulId).resolve("skills").resolve(packId);
    }

    @Test
    void loadsFromSkillYamlAndBodyPath() throws Exception {
        String soul = "test_soul";
        Path dir = soulSkillPack(workDir, soul, "alpha");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("skill.yaml"), """
                name: Alpha Skill
                description: Beta summary line.
                """);
        Files.writeString(dir.resolve("SKILL.md"), "THIS WOULD BREAK IF PARSED AS YAML");

        List<LoadedSkill> skills = SkillLoader.loadOwnSkills(workDir, soul);
        assertEquals(1, skills.size());
        assertEquals("alpha", skills.get(0).id());
        assertEquals("Alpha Skill", skills.get(0).name());
        assertEquals("Beta summary line.", skills.get(0).summary());
        assertFalse(skills.get(0).sharedPack());
        assertNotNull(skills.get(0).skillFile());
        assertTrue(Files.isSameFile(skills.get(0).skillFile(), dir.resolve("SKILL.md")));
        assertTrue(Files.isSameFile(skills.get(0).skillDirectory(), dir));
    }

    @Test
    void skillYamlWithoutSkillMd() throws Exception {
        Path dir = soulSkillPack(workDir, "x", "meta-only");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("skill.yaml"), """
                name: Meta Only
                description: No body file.
                """);

        List<LoadedSkill> skills = SkillLoader.loadOwnSkills(workDir, "x");
        assertEquals(1, skills.size());
        assertNull(skills.get(0).skillFile());
        assertEquals("No body file.", skills.get(0).summary());
    }

    @Test
    void loadsSkillMetaJson() throws Exception {
        Path dir = soulSkillPack(workDir, "x", "json-skill");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("skill.meta.json"), """
                {"name":"Json Name","summary":"from summary key"}
                """);

        List<LoadedSkill> skills = SkillLoader.loadOwnSkills(workDir, "x");
        assertEquals(1, skills.size());
        assertEquals("Json Name", skills.get(0).name());
        assertEquals("from summary key", skills.get(0).summary());
    }

    @Test
    void legacySkillMdOnly() throws Exception {
        Path dir = soulSkillPack(workDir, "x", "legacy");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("SKILL.md"), """
                ---
                name: Nice Name
                description: Short desc
                ---
                # Ignored title
                Body line.
                """);

        List<LoadedSkill> skills = SkillLoader.loadOwnSkills(workDir, "x");
        assertEquals(1, skills.size());
        assertEquals("legacy", skills.get(0).id());
        assertEquals("Nice Name", skills.get(0).name());
        assertEquals("Short desc", skills.get(0).summary());
        assertNotNull(skills.get(0).skillFile());
    }

    @Test
    void listsReferencesAndPrefersScriptsFolder() throws Exception {
        Path dir = soulSkillPack(workDir, "s", "k");
        Files.createDirectories(dir.resolve("references"));
        Files.createDirectories(dir.resolve("scripts"));
        Files.writeString(dir.resolve("skill.yaml"), "name: X\ndescription: Y\n");
        Files.writeString(dir.resolve("references").resolve("a.md"), "r");
        Files.writeString(dir.resolve("scripts").resolve("run.sh"), "x");

        List<LoadedSkill> skills = SkillLoader.loadOwnSkills(workDir, "s");
        assertEquals(1, skills.size());
        assertEquals(List.of("a.md"), skills.get(0).referenceFileNames());
        assertEquals(List.of("run.sh"), skills.get(0).scriptFileNames());
        assertTrue(Files.isSameFile(skills.get(0).scriptsDirectory(), dir.resolve("scripts")));
    }

    @Test
    void sharedPackUnderZcodeSkills() throws Exception {
        Path shared = workDir.resolve(".zclaw/skills").resolve("common");
        Files.createDirectories(shared);
        Files.writeString(shared.resolve("skill.yaml"), "name: Shared\ndescription: All souls.\n");
        Path local = soulSkillPack(workDir, "u", "mine");
        Files.createDirectories(local);
        Files.writeString(local.resolve("skill.yaml"), "name: Local\ndescription: One soul.\n");

        List<LoadedSkill> skills = SkillLoader.loadOwnSkills(workDir, "u");
        assertEquals(2, skills.size());
        LoadedSkill sh = skills.stream().filter(s -> "common".equals(s.id())).findFirst().orElseThrow();
        LoadedSkill lo = skills.stream().filter(s -> "mine".equals(s.id())).findFirst().orElseThrow();
        assertTrue(sh.sharedPack());
        assertFalse(lo.sharedPack());
    }

    @Test
    void legacySoulNamedSkillsDirStillWorks() throws Exception {
        Path dir = workDir.resolve(".zclaw/skills").resolve("legacySoul").resolve("oldpack");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("skill.yaml"), "name: Old\ndescription: Legacy path.\n");
        List<LoadedSkill> skills = SkillLoader.loadOwnSkills(workDir, "legacySoul");
        assertEquals(1, skills.size());
        assertEquals("oldpack", skills.get(0).id());
        assertFalse(skills.get(0).sharedPack());
    }

    @Test
    void manifestIncludesAuxFileNames() throws Exception {
        Path dir = soulSkillPack(workDir, "gg", "s2");
        Files.createDirectories(dir.resolve("references"));
        Files.createDirectories(dir.resolve("scripts"));
        Files.writeString(dir.resolve("skill.yaml"), "name: T\ndescription: D\n");
        Files.writeString(dir.resolve("SKILL.md"), "x\n");
        Files.writeString(dir.resolve("references").resolve("r.md"), "r");
        Files.writeString(dir.resolve("scripts").resolve("s.sh"), "s");
        List<LoadedSkill> own = SkillLoader.loadOwnSkills(workDir, "gg");
        SkillManifestStore.publishIfChanged(workDir, "gg", own);
        SkillManifestDto read = SkillManifestStore.readManifest(workDir, "gg");
        assertNotNull(read);
        assertEquals(1, read.getSkills().size());
        assertEquals(List.of("r.md"), read.getSkills().get(0).getReferenceFiles());
        assertEquals(List.of("s.sh"), read.getSkills().get(0).getScriptFiles());
    }

    @Test
    void manifestRoundTrip() throws Exception {
        Path dir = soulSkillPack(workDir, "gg", "s1");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("skill.yaml"), "name: One\ndescription: Hi.\n");
        Files.writeString(dir.resolve("SKILL.md"), "# x\n");
        List<LoadedSkill> own = SkillLoader.loadOwnSkills(workDir, "gg");
        SkillManifestStore.publishIfChanged(workDir, "gg", own);
        SkillManifestDto read = SkillManifestStore.readManifest(workDir, "gg");
        assertNotNull(read);
        assertEquals(1, read.getSkills().size());
        assertEquals("s1", read.getSkills().get(0).getId());
    }
}
