package com.zxx.zclaw.memory;

import com.zxx.zclaw.config.ZClawPaths;
import com.zxx.zclaw.soul.SoulProfile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Long-term memory file: per-soul under {@code .zclaw/souls/<id>/memory.md}; default soul also checks legacy {@code memory.md} at work dir root.
 */
public final class ProjectMemory {

    public static final String FILE_NAME = "memory.md";

    private ProjectMemory() {}

    public static Path path(Path workDir, SoulProfile soul) {
        if (soul == null || soul.isDefault()) {
            Path legacy = workDir.resolve(FILE_NAME).normalize();
            if (Files.isRegularFile(legacy)) {
                return legacy;
            }
            return ZClawPaths.soulHome(workDir, "default").resolve(FILE_NAME).normalize();
        }
        return ZClawPaths.soulHome(workDir, soul.getId()).resolve(FILE_NAME).normalize();
    }

    /** @deprecated use {@link #path(Path, SoulProfile)} */
    @Deprecated
    public static Path path(Path workDir) {
        return path(workDir, SoulProfile.defaultProfile());
    }

    public static String load(Path workDir, SoulProfile soul) throws IOException {
        Path p = path(workDir, soul);
        if (!Files.isRegularFile(p)) {
            return "";
        }
        return Files.readString(p, StandardCharsets.UTF_8).trim();
    }

    /** @deprecated use {@link #load(Path, SoulProfile)} */
    @Deprecated
    public static String load(Path workDir) throws IOException {
        return load(workDir, SoulProfile.defaultProfile());
    }

    public static void save(Path workDir, SoulProfile soul, String content) throws IOException {
        Path p = path(workDir, soul);
        Files.createDirectories(p.getParent());
        String body = content == null ? "" : content.stripTrailing();
        if (!body.isEmpty() && !body.endsWith("\n")) {
            body = body + "\n";
        }
        Files.writeString(p, body, StandardCharsets.UTF_8);
    }

    /** @deprecated use {@link #save(Path, SoulProfile, String)} */
    @Deprecated
    public static void save(Path workDir, String content) throws IOException {
        save(workDir, SoulProfile.defaultProfile(), content);
    }
}
