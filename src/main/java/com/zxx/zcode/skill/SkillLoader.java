package com.zxx.zcode.skill;

import com.zxx.zcode.config.ZCodePaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Loads skills: shared {@code .zcode/skills/<pack>/} then per-soul {@code .zcode/souls/<id>/skills/<pack>/}.
 * Legacy {@code .zcode/skills/<soulId>/<pack>/} is still scanned for migration. User home and work dir merge with work winning.
 */
public final class SkillLoader {

    private static final Logger log = LoggerFactory.getLogger(SkillLoader.class);
    private static final String SKILL_FILE = "SKILL.md";
    private static final String META_YAML = "skill.yaml";
    private static final String META_JSON = "skill.meta.json";

    private SkillLoader() {}

    public static List<LoadedSkill> loadOwnSkills(Path workDir, String soulId) {
        if (workDir == null || !ZCodePaths.safeSoulId(soulId)) {
            return List.of();
        }
        Map<String, LoadedSkill> byId = new LinkedHashMap<>();
        loadFromRoot(ZCodePaths.userSharedSkillsDir(), byId, false, true);
        loadLegacySoulSkillContainer(ZCodePaths.userZcodeDir(), soulId, byId, true);
        loadFromRoot(ZCodePaths.userSoulSkillsDir(soulId), byId, true, false);
        loadFromRoot(ZCodePaths.sharedSkillsDir(workDir), byId, true, true);
        loadLegacySoulSkillContainer(ZCodePaths.zcodeDir(workDir), soulId, byId, true);
        loadFromRoot(ZCodePaths.soulSkillsDir(workDir, soulId), byId, true, false);
        return List.copyOf(byId.values());
    }

    /** Legacy: {@code <zcodeRoot>/skills/<soulId>/<skillFolder>/} (pre–soul-home layout). */
    private static void loadLegacySoulSkillContainer(Path zcodeRoot, String soulId, Map<String, LoadedSkill> byId, boolean override) {
        if (zcodeRoot == null || !Files.isDirectory(zcodeRoot)) {
            return;
        }
        Path container = zcodeRoot.resolve(ZCodePaths.SKILLS).resolve(soulId.trim());
        if (!Files.isDirectory(container)) {
            return;
        }
        loadFromRoot(container, byId, override, false);
    }

    private static void loadFromRoot(Path root, Map<String, LoadedSkill> byId, boolean overrideExisting, boolean sharedPack) {
        if (root == null || !Files.isDirectory(root)) {
            return;
        }
        try (Stream<Path> stream = Files.list(root)) {
            stream.filter(Files::isDirectory).sorted().forEach(sub -> {
                String folder = sub.getFileName().toString();
                if (!ZCodePaths.safeSkillFolderName(folder)) {
                    log.warn("Skipping unsafe skill folder name: {}", folder);
                    return;
                }
                if (!overrideExisting && byId.containsKey(folder)) {
                    return;
                }
                LoadedSkill loaded = tryLoadSkillFolder(sub, folder, sharedPack);
                if (loaded != null) {
                    byId.put(folder, loaded);
                }
            });
        } catch (IOException e) {
            log.warn("Failed to list skills under {}: {}", root, e.getMessage());
        }
    }

    private static LoadedSkill tryLoadSkillFolder(Path skillDir, String folderId, boolean sharedPack) {
        Path yaml = skillDir.resolve(META_YAML);
        Path json = skillDir.resolve(META_JSON);
        Path md = skillDir.resolve(SKILL_FILE);

        try {
            if (Files.isRegularFile(yaml)) {
                SkillMetaLoader.Meta meta = SkillMetaLoader.loadFromYaml(yaml);
                Path body = Files.isRegularFile(md) ? md : null;
                return assemble(skillDir, folderId, meta.effectiveName(folderId), meta.effectiveDescription(), body, sharedPack);
            }
            if (Files.isRegularFile(json)) {
                SkillMetaLoader.Meta meta = SkillMetaLoader.loadFromJson(json);
                Path body = Files.isRegularFile(md) ? md : null;
                return assemble(skillDir, folderId, meta.effectiveName(folderId), meta.effectiveDescription(), body, sharedPack);
            }
            if (Files.isRegularFile(md)) {
                String raw = Files.readString(md);
                SkillMarkdownParser.Parsed p = SkillMarkdownParser.parse(raw);
                return assemble(skillDir, folderId, p.name(), p.summary(), md, sharedPack);
            }
        } catch (IOException e) {
            log.warn("Failed to load skill folder {}: {}", skillDir, e.getMessage());
        }
        return null;
    }

    private static LoadedSkill assemble(Path skillDir, String folderId, String name, String summary, Path skillFile, boolean sharedPack) {
        SkillAuxLister.Aux aux = SkillAuxLister.scan(skillDir);
        return new LoadedSkill(
                folderId,
                name,
                summary,
                skillDir,
                skillFile,
                aux.referenceFileNames(),
                aux.scriptsDirectory(),
                aux.scriptFileNames(),
                sharedPack);
    }
}
