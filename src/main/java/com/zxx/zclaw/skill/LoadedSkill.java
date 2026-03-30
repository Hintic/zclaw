package com.zxx.zclaw.skill;

import java.nio.file.Path;
import java.util.List;

/**
 * One skill folder. {@code sharedPack} is true when loaded from {@code .zclaw/skills/} (all souls);
 * false when from {@code .zclaw/souls/<id>/skills/} or legacy {@code .zclaw/skills/<id>/…}.
 */
public record LoadedSkill(
        String id,
        String name,
        String summary,
        Path skillDirectory,
        Path skillFile,
        List<String> referenceFileNames,
        Path scriptsDirectory,
        List<String> scriptFileNames,
        boolean sharedPack) {

    public LoadedSkill {
        referenceFileNames = referenceFileNames == null ? List.of() : List.copyOf(referenceFileNames);
        scriptFileNames = scriptFileNames == null ? List.of() : List.copyOf(scriptFileNames);
    }
}
