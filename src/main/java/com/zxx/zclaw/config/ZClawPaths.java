package com.zxx.zclaw.config;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Layout under {@code <workDir>/.zclaw/} and {@code ~/.zclaw/}:
 * <ul>
 *   <li>{@code souls/<id>/} — per-soul home: {@code soul.json}, {@code skills/}, {@code memory.md}, {@code inbox.jsonl}, {@code mood.json}</li>
 *   <li>{@code skills/} — shared skill packs (all souls)</li>
 *   <li>{@code mcp/} — reserved for future shared MCP assets</li>
 *   <li>{@code skill-manifests/} — published skill metadata per soul (for peers)</li>
 * </ul>
 * Legacy paths ({@code souls/<id>.json}, {@code skills/<id>/…}, {@code soul_inbox/}, {@code soul_mood/}, root {@code memory.md}) are still read when newer files are absent.
 */
public final class ZClawPaths {

    public static final String DOT_ZCLAW = ".zclaw";
    public static final String SOULS = "souls";
    public static final String SKILLS = "skills";
    public static final String MCP = "mcp";
    public static final String SKILL_MANIFESTS = "skill-manifests";

    /** Primary soul profile file inside {@code souls/<id>/}. */
    public static final String SOUL_JSON = "soul.json";

    private ZClawPaths() {}

    public static Path zclawDir(Path workDir) {
        return workDir.resolve(DOT_ZCLAW);
    }

    /**
     * Per-user layout under the OS home directory ({@code ~/.zclaw}), used for souls and shared skills
     * when not overridden by env. Distinct from {@link AgentConfig#getConfigDir()} (defaults to
     * {@code user.dir/.zclaw} for {@code config.json}).
     */
    public static Path userHomeZclawDir() {
        String home = System.getProperty("user.home");
        if (home == null || home.isBlank()) {
            return AgentConfig.getConfigDir();
        }
        return Paths.get(home).resolve(DOT_ZCLAW);
    }

    public static Path userZclawDir() {
        return userHomeZclawDir();
    }

    public static Path soulHome(Path workDir, String soulId) {
        return zclawDir(workDir).resolve(SOULS).resolve(soulId.trim());
    }

    public static Path userSoulHome(String soulId) {
        return userZclawDir().resolve(SOULS).resolve(soulId.trim());
    }

    /** Shared skills: {@code .zclaw/skills/<skillFolder>/} */
    public static Path sharedSkillsDir(Path workDir) {
        return zclawDir(workDir).resolve(SKILLS);
    }

    public static Path userSharedSkillsDir() {
        return userZclawDir().resolve(SKILLS);
    }

    /** Per-soul skills: {@code .zclaw/souls/<id>/skills/<skillFolder>/} */
    public static Path soulSkillsDir(Path workDir, String soulId) {
        return soulHome(workDir, soulId).resolve(SKILLS);
    }

    public static Path userSoulSkillsDir(String soulId) {
        return userSoulHome(soulId).resolve(SKILLS);
    }

    public static Path mcpDir(Path workDir) {
        return zclawDir(workDir).resolve(MCP);
    }

    public static Path skillManifestFile(Path workDir, String soulId) {
        return zclawDir(workDir).resolve(SKILL_MANIFESTS).resolve(safeSoulIdSegment(soulId) + ".json");
    }

    public static boolean safeSoulId(String soulId) {
        if (soulId == null || soulId.isBlank()) {
            return false;
        }
        return soulId.trim().matches("[a-zA-Z0-9_-]+");
    }

    private static String safeSoulIdSegment(String soulId) {
        return safeSoulId(soulId) ? soulId.trim() : "unknown";
    }

    public static boolean safeSkillFolderName(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        String t = name.trim();
        if (t.contains("..") || t.contains("/") || t.contains("\\")) {
            return false;
        }
        return t.matches("[a-zA-Z0-9][a-zA-Z0-9._-]*");
    }

    public static boolean safeAuxFileName(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        String t = name.trim();
        if (t.contains("..") || t.contains("/") || t.contains("\\")) {
            return false;
        }
        return t.matches("[a-zA-Z0-9._-]+");
    }
}
