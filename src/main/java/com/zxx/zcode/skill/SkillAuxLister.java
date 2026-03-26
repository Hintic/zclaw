package com.zxx.zcode.skill;

import com.zxx.zcode.config.ZCodePaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Lists file names under {@code references/} and {@code scripts/} or {@code script/} without reading file contents.
 */
final class SkillAuxLister {

    private SkillAuxLister() {}

    record Aux(List<String> referenceFileNames, Path scriptsDirectory, List<String> scriptFileNames) {}

    static Aux scan(Path skillDirectory) {
        if (skillDirectory == null || !Files.isDirectory(skillDirectory)) {
            return new Aux(List.of(), null, List.of());
        }
        List<String> refs = listSafeFileNames(skillDirectory.resolve("references"));
        Path scriptsRoot = null;
        Path scripts = skillDirectory.resolve("scripts");
        Path script = skillDirectory.resolve("script");
        if (Files.isDirectory(scripts)) {
            scriptsRoot = scripts;
        } else if (Files.isDirectory(script)) {
            scriptsRoot = script;
        }
        List<String> scriptNames = scriptsRoot != null ? listSafeFileNames(scriptsRoot) : List.of();
        return new Aux(refs, scriptsRoot, scriptNames);
    }

    private static List<String> listSafeFileNames(Path dir) {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(ZCodePaths::safeAuxFileName)
                    .sorted()
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }
}
