package com.zxx.zcode.skill;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Loads {@code skill.yaml} or {@code skill.meta.json} only — never reads {@code SKILL.md}.
 */
final class SkillMetaLoader {

    private static final Gson GSON = new Gson();
    private static final Yaml YAML = new Yaml(new SafeConstructor(new LoaderOptions()));

    private SkillMetaLoader() {}

    static Meta loadFromYaml(Path path) throws IOException {
        Object root = YAML.load(Files.readString(path));
        if (!(root instanceof Map<?, ?> m)) {
            return Meta.empty();
        }
        return fromMap(m);
    }

    static Meta loadFromJson(Path path) throws IOException {
        JsonDto dto = GSON.fromJson(Files.readString(path), JsonDto.class);
        if (dto == null) {
            return Meta.empty();
        }
        String name = blankToNull(dto.name);
        String desc = blankToNull(dto.description);
        if (desc == null) {
            desc = blankToNull(dto.summary);
        }
        return new Meta(name, desc);
    }

    private static Meta fromMap(Map<?, ?> m) {
        String name = stringVal(m.get("name"));
        String desc = stringVal(m.get("description"));
        if (desc == null) {
            desc = stringVal(m.get("summary"));
        }
        return new Meta(name, desc);
    }

    private static String stringVal(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof String s) {
            return blankToNull(s);
        }
        return blankToNull(o.toString());
    }

    private static String blankToNull(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s.trim();
    }

    record Meta(String name, String description) {
        static Meta empty() {
            return new Meta(null, null);
        }

        String effectiveName(String folderId) {
            if (name != null && !name.isBlank()) {
                return name.trim();
            }
            return humanizeFolder(folderId);
        }

        String effectiveDescription() {
            if (description != null && !description.isBlank()) {
                return clamp(description.trim(), 500);
            }
            return "(Open SKILL.md with read_file when this skill applies.)";
        }
    }

    private static String humanizeFolder(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "Skill";
        }
        String t = raw.replace('-', ' ').replace('_', ' ');
        return Character.toUpperCase(t.charAt(0)) + t.substring(1);
    }

    private static String clamp(String s, int max) {
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max - 3) + "...";
    }

    @SuppressWarnings("unused")
    private static final class JsonDto {
        String name;
        String description;
        @SerializedName("summary")
        String summary;
    }
}
