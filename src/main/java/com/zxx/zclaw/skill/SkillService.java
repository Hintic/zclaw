package com.zxx.zclaw.skill;

import com.zxx.zclaw.config.ZClawPaths;
import com.zxx.zclaw.soul.SoulProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Loads per-soul skills, publishes this soul's manifest for peers, and formats system-prompt sections.
 */
public final class SkillService {

    private static final Logger log = LoggerFactory.getLogger(SkillService.class);

    private SkillService() {}

    /**
     * Reloads skills from disk, updates manifest, appends "Your skills" and "Peer souls' skills" sections.
     */
    public static void appendSkillSections(StringBuilder sb, Path workDir, SoulProfile soul) {
        if (soul == null || workDir == null) {
            return;
        }
        List<LoadedSkill> own = SkillLoader.loadOwnSkills(workDir, soul.getId());
        try {
            SkillManifestStore.publishIfChanged(workDir, soul.getId(), own);
        } catch (IOException e) {
            log.warn("Skill manifest publish failed: {}", e.getMessage());
        }

        boolean hasPeers = !soul.getPeers().isEmpty();
        if (own.isEmpty() && !hasPeers) {
            return;
        }

        if (!own.isEmpty()) {
            sb.append("\n## Your skills (progressive disclosure)\n");
            sb.append("Shared packs: `.zclaw/skills/<pack>/` (all souls). This soul only: `.zclaw/souls/");
            sb.append(soul.getId()).append("/skills/<pack>/` (legacy: `.zclaw/skills/");
            sb.append(soul.getId()).append("/<pack>/`). ");
            sb.append("Catalog comes from skill.yaml or skill.meta.json (SKILL.md is not read on refresh). ");
            sb.append("SKILL.md should describe optional subfolders: references/ (extra docs) and scripts/ or script/ ");
            sb.append("(runnable files). After reading SKILL.md, call read_file on a reference only when needed; ");
            sb.append("run a script via bash only when the task requires it. Below lists file names only (paths absolute).\n\n");
            for (LoadedSkill s : own) {
                Path dir = s.skillDirectory().toAbsolutePath().normalize();
                sb.append("- **`").append(s.id()).append("`** — ").append(s.name());
                sb.append(s.sharedPack() ? " *(shared — all souls)*\n" : " *(this soul only)*\n");
                sb.append("  - Description: ").append(s.summary()).append("\n");
                sb.append("  - Instructions file: ");
                if (s.skillFile() != null) {
                    sb.append("`").append(s.skillFile().toAbsolutePath().normalize()).append("`\n");
                } else {
                    sb.append("(add SKILL.md in this folder for full instructions)\n");
                }
                Path refsDir = dir.resolve("references");
                sb.append("  - references/: `").append(refsDir).append("`");
                if (s.referenceFileNames().isEmpty()) {
                    sb.append(" (no listed files)\n");
                } else {
                    sb.append(" — ").append(String.join(", ", s.referenceFileNames())).append("\n");
                }
                if (s.scriptsDirectory() != null && !s.scriptFileNames().isEmpty()) {
                    sb.append("  - scripts: `").append(s.scriptsDirectory().toAbsolutePath().normalize());
                    sb.append("` — ").append(String.join(", ", s.scriptFileNames())).append("\n");
                } else if (s.scriptsDirectory() != null) {
                    sb.append("  - scripts: `").append(s.scriptsDirectory().toAbsolutePath().normalize());
                    sb.append("` (no listed files)\n");
                } else {
                    sb.append("  - scripts/: (no scripts/ or script/ directory)\n");
                }
            }
            sb.append("\n");
        }

        if (hasPeers) {
            sb.append("\n## Peer souls' skills (metadata only)\n");
            sb.append("You do not have the peer's SKILL.md files. If the user's task clearly matches a peer skill ");
            sb.append("better than your own, send soul_mail with kind=skill_suggest: name the skill id, summarize ");
            sb.append("the task, and ask them to take it.\n\n");
            for (String peer : soul.getPeers()) {
                if (!ZClawPaths.safeSoulId(peer)) {
                    continue;
                }
                SkillManifestDto m = SkillManifestStore.readManifest(workDir, peer.trim());
                sb.append("- **Soul `").append(peer.trim()).append("`**");
                if (m == null || m.getSkills() == null || m.getSkills().isEmpty()) {
                    sb.append(": (no manifest — peer not run yet or no skills)\n");
                } else {
                    sb.append(":\n");
                    for (SkillManifestDto.Entry e : m.getSkills()) {
                        sb.append("  - `").append(e.getId()).append("` **");
                        sb.append(e.getName() != null ? e.getName() : e.getId()).append("** — ");
                        sb.append(e.getSummary() != null ? e.getSummary() : "").append("\n");
                        appendPeerAuxLine(sb, "    reference files", e.getReferenceFiles());
                        appendPeerAuxLine(sb, "    script files", e.getScriptFiles());
                    }
                }
            }
            sb.append("\n");
        }
    }

    /** For /status: local skill count + whether each peer has a manifest. */
    public static String statusLine(Path workDir, SoulProfile soul) {
        if (soul == null || workDir == null) {
            return "Skills: n/a";
        }
        int n = SkillLoader.loadOwnSkills(workDir, soul.getId()).size();
        if (soul.getPeers().isEmpty()) {
            return "Skills: " + n + " loaded (shared + this soul; see system prompt)";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Skills: ").append(n).append(" loaded; peers: ");
        boolean first = true;
        for (String p : soul.getPeers()) {
            if (!ZClawPaths.safeSoulId(p)) {
                continue;
            }
            if (!first) {
                sb.append(", ");
            }
            first = false;
            SkillManifestDto m = SkillManifestStore.readManifest(workDir, p.trim());
            int c = m != null && m.getSkills() != null ? m.getSkills().size() : 0;
            sb.append("`").append(p.trim()).append("` manifest ");
            sb.append(c > 0 ? c + " skill(s)" : "(none)");
        }
        return sb.toString();
    }

    private static void appendPeerAuxLine(StringBuilder sb, String label, List<String> names) {
        if (names == null || names.isEmpty()) {
            return;
        }
        sb.append(label).append(": ").append(String.join(", ", names)).append("\n");
    }
}
