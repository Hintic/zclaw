package com.zxx.zclaw.skill;

import java.util.Locale;

/**
 * Parses optional YAML-like frontmatter and title/summary from {@code SKILL.md}.
 */
final class SkillMarkdownParser {

    private SkillMarkdownParser() {}

    static Parsed parse(String raw) {
        if (raw == null) {
            return new Parsed("", "");
        }
        String text = raw.replace("\r\n", "\n");
        String rest = text;
        String name = null;
        String summary = null;

        if (text.startsWith("---")) {
            int end = text.indexOf("\n---", 3);
            if (end > 0) {
                String fm = text.substring(3, end).trim();
                rest = text.substring(end + 4).trim();
                for (String line : fm.split("\n")) {
                    int c = line.indexOf(':');
                    if (c <= 0) {
                        continue;
                    }
                    String k = line.substring(0, c).trim().toLowerCase(Locale.ROOT);
                    String v = line.substring(c + 1).trim();
                    if ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'"))) {
                        v = v.substring(1, v.length() - 1);
                    }
                    if ("name".equals(k)) {
                        name = v;
                    } else if ("description".equals(k) || "summary".equals(k)) {
                        summary = v;
                    }
                }
            }
        }

        rest = rest.stripLeading();
        if (name == null || name.isBlank()) {
            String firstLine = firstNonEmptyLine(rest);
            if (firstLine.startsWith("#")) {
                name = firstLine.replaceFirst("^#+\\s*", "").trim();
                rest = stripFirstLine(rest);
            }
        }
        if (name == null || name.isBlank()) {
            name = "Skill";
        }

        if (summary == null || summary.isBlank()) {
            summary = firstParagraph(rest);
        }
        if (summary.length() > 500) {
            summary = summary.substring(0, 497) + "...";
        }

        return new Parsed(name.trim(), summary.trim());
    }

    private static String firstNonEmptyLine(String s) {
        for (String line : s.split("\n")) {
            if (!line.isBlank()) {
                return line.trim();
            }
        }
        return "";
    }

    private static String stripFirstLine(String s) {
        int nl = s.indexOf('\n');
        if (nl < 0) {
            return "";
        }
        return s.substring(nl + 1).stripLeading();
    }

    private static String firstParagraph(String s) {
        StringBuilder sb = new StringBuilder();
        for (String line : s.split("\n")) {
            if (line.isBlank()) {
                if (!sb.isEmpty()) {
                    break;
                }
                continue;
            }
            if (line.startsWith("#")) {
                if (!sb.isEmpty()) {
                    break;
                }
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(line.trim());
            if (sb.length() > 400) {
                break;
            }
        }
        String out = sb.toString().trim();
        return out.isEmpty() ? "(no summary)" : out;
    }

    record Parsed(String name, String summary) {}
}
