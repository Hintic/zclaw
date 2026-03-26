package com.zxx.zcode.soul;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.zxx.zcode.config.AgentConfig;
import com.zxx.zcode.config.ZCodePaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Loads soul definitions from {@code <workDir>/.zcode/souls/<id>/soul.json}, then {@code ~/.zcode/souls/<id>/soul.json},
 * with legacy fallback to {@code souls/<id>.json} at the same roots.
 */
public final class SoulLoader {

    private static final Logger log = LoggerFactory.getLogger(SoulLoader.class);
    private static final Gson GSON = new Gson();

    private SoulLoader() {}

    public static SoulProfile load(String requestedId, Path workDir) {
        String id = requestedId == null ? "" : requestedId.trim();
        if (id.isEmpty()) {
            return SoulProfile.defaultProfile();
        }

        Optional<Path> resolved = resolveSoulFile(id, workDir);
        if (resolved.isEmpty()) {
            Path w1 = workDir != null ? ZCodePaths.soulHome(workDir, id).resolve(ZCodePaths.SOUL_JSON) : null;
            Path w2 = workDir != null ? ZCodePaths.zcodeDir(workDir).resolve(ZCodePaths.SOULS).resolve(id + ".json") : null;
            Path u1 = ZCodePaths.userSoulHome(id).resolve(ZCodePaths.SOUL_JSON);
            Path u2 = AgentConfig.getConfigDir().resolve(ZCodePaths.SOULS).resolve(id + ".json");
            log.info("No soul file for id '{}' (tried {}, {}, {}, {}); using inline empty persona",
                    id, w1, w2, u1, u2);
            return new SoulProfile(id, humanize(id), "", List.of());
        }

        Path file = resolved.get();
        try {
            String json = Files.readString(file);
            FileDto dto = GSON.fromJson(json, FileDto.class);
            if (dto == null) {
                return new SoulProfile(id, humanize(id), "", List.of());
            }
            String canonId = dto.id != null && !dto.id.isBlank() ? dto.id.trim() : id;
            String name = dto.displayName != null && !dto.displayName.isBlank()
                    ? dto.displayName.trim() : humanize(canonId);
            String persona = dto.persona != null ? dto.persona : "";
            List<String> peers = dto.peers != null ? new ArrayList<>(dto.peers) : List.of();
            log.info("Loaded soul '{}' from {}", canonId, file);
            return new SoulProfile(canonId, name, persona, peers);
        } catch (IOException e) {
            log.error("Failed to read soul file {}: {}", file, e.getMessage());
            return new SoulProfile(id, humanize(id), "", List.of());
        }
    }

    public static Optional<Path> resolveSoulFile(String soulId, Path workDir) {
        if (soulId == null || soulId.isBlank()) {
            return Optional.empty();
        }
        String safeId = soulId.trim();

        if (workDir != null) {
            Path nested = ZCodePaths.soulHome(workDir, safeId).resolve(ZCodePaths.SOUL_JSON);
            if (Files.isRegularFile(nested)) {
                return Optional.of(nested);
            }
            Path flat = ZCodePaths.zcodeDir(workDir).resolve(ZCodePaths.SOULS).resolve(safeId + ".json");
            if (Files.isRegularFile(flat)) {
                return Optional.of(flat);
            }
        }
        Path userNested = ZCodePaths.userSoulHome(safeId).resolve(ZCodePaths.SOUL_JSON);
        if (Files.isRegularFile(userNested)) {
            return Optional.of(userNested);
        }
        Path userFlat = AgentConfig.getConfigDir().resolve(ZCodePaths.SOULS).resolve(safeId + ".json");
        if (Files.isRegularFile(userFlat)) {
            return Optional.of(userFlat);
        }
        return Optional.empty();
    }

    private static String humanize(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "z-code";
        }
        return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
    }

    @SuppressWarnings("unused")
    private static final class FileDto {
        String id;
        @SerializedName("display_name")
        String displayName;
        String persona;
        List<String> peers;
    }
}
