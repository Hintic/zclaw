package com.zxx.zclaw.tool;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Collectors;

public class GlobTool implements Tool {

    private final Path workDir;

    public GlobTool(Path workDir) {
        this.workDir = workDir;
    }

    @Override
    public String name() { return "glob"; }

    @Override
    public String description() {
        return "Find files matching a glob pattern. Returns file paths relative to the working directory. " +
                "Examples: '**/*.java', 'src/**/*.xml', '**/Controller*.java'";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("pattern", Map.of("type", "string", "description", "Glob pattern (e.g. '**/*.java')"));
        props.put("path", Map.of("type", "string", "description", "Directory to search in (default: work dir)"));

        return Map.of(
                "type", "object",
                "properties", props,
                "required", List.of("pattern")
        );
    }

    @Override
    public String execute(Map<String, Object> args) {
        String pattern = (String) args.get("pattern");
        String searchPath = (String) args.get("path");

        if (pattern == null || pattern.isBlank()) {
            return "Error: pattern is required";
        }

        Path searchDir = (searchPath != null && !searchPath.isBlank())
                ? resolvePath(searchPath)
                : workDir;

        if (!Files.isDirectory(searchDir)) {
            return "Error: directory not found: " + searchDir;
        }

        try {
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
            List<String> matches = new ArrayList<>();

            Files.walkFileTree(searchDir, EnumSet.of(FileVisitOption.FOLLOW_LINKS), 20, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    Path relative = searchDir.relativize(file);
                    if (matcher.matches(relative)) {
                        matches.add(relative.toString());
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    String dirName = dir.getFileName() != null ? dir.getFileName().toString() : "";
                    if (dirName.equals(".git") || dirName.equals("node_modules") || dirName.equals("target") || dirName.equals(".idea")) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });

            Collections.sort(matches);
            if (matches.isEmpty()) {
                return "No files matched pattern: " + pattern;
            }
            return matches.size() + " files matched:\n" + String.join("\n", matches);
        } catch (IOException e) {
            return "Error searching files: " + e.getMessage();
        }
    }

    private Path resolvePath(String filePath) {
        Path p = Paths.get(filePath);
        if (p.isAbsolute()) return p;
        return workDir.resolve(p);
    }
}
