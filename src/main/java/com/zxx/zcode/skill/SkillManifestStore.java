package com.zxx.zcode.skill;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.zxx.zcode.config.ZCodePaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads/writes per-soul skill manifests under {@code <workDir>/.zcode/skill-manifests/}.
 */
public final class SkillManifestStore {

    private static final Logger log = LoggerFactory.getLogger(SkillManifestStore.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private SkillManifestStore() {}

    public static Path manifestPath(Path workDir, String soulId) {
        return ZCodePaths.skillManifestFile(workDir, soulId);
    }

    /**
     * Writes manifest only if JSON content changed (avoids noisy updates).
     */
    public static void publishIfChanged(Path workDir, String soulId, List<LoadedSkill> skills) throws IOException {
        if (workDir == null || !ZCodePaths.safeSoulId(soulId)) {
            return;
        }
        SkillManifestDto dto = new SkillManifestDto();
        dto.setSoulId(soulId.trim());
        dto.setUpdatedAtEpochMs(System.currentTimeMillis());
        List<SkillManifestDto.Entry> entries = new ArrayList<>();
        for (LoadedSkill s : skills) {
            entries.add(new SkillManifestDto.Entry(
                    s.id(),
                    s.name(),
                    s.summary(),
                    s.referenceFileNames().isEmpty() ? null : new ArrayList<>(s.referenceFileNames()),
                    s.scriptFileNames().isEmpty() ? null : new ArrayList<>(s.scriptFileNames())));
        }
        dto.setSkills(entries);
        String json = GSON.toJson(dto);
        Path path = manifestPath(workDir, soulId);
        Files.createDirectories(path.getParent());
        if (Files.isRegularFile(path)) {
            String prev = Files.readString(path);
            if (prev.equals(json)) {
                return;
            }
        }
        Files.writeString(path, json);
        log.info("Published skill manifest for soul '{}' ({} skills) -> {}", soulId, skills.size(), path);
    }

    public static SkillManifestDto readManifest(Path workDir, String peerSoulId) {
        if (workDir == null || !ZCodePaths.safeSoulId(peerSoulId)) {
            return null;
        }
        Path path = manifestPath(workDir, peerSoulId);
        if (!Files.isRegularFile(path)) {
            return null;
        }
        try {
            return GSON.fromJson(Files.readString(path), SkillManifestDto.class);
        } catch (IOException e) {
            log.warn("Failed to read skill manifest {}: {}", path, e.getMessage());
            return null;
        }
    }
}
