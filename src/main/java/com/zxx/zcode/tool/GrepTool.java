package com.zxx.zcode.tool;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GrepTool implements Tool {

    private final Path workDir;

    public GrepTool(Path workDir) {
        this.workDir = workDir;
    }

    @Override
    public String name() { return "grep"; }

    @Override
    public String description() {
        return "Search file contents using a regex pattern. Returns matching lines with file paths and line numbers. " +
                "Skips binary files and common non-source directories (.git, node_modules, target).";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("pattern", Map.of("type", "string", "description", "Regex pattern to search for"));
        props.put("path", Map.of("type", "string", "description", "Directory or file to search (default: work dir)"));
        props.put("include", Map.of("type", "string", "description", "Glob to filter files (e.g. '*.java')"));
        props.put("case_insensitive", Map.of("type", "boolean", "description", "Case insensitive search (default: false)"));

        return Map.of(
                "type", "object",
                "properties", props,
                "required", List.of("pattern")
        );
    }

    @Override
    public String execute(Map<String, Object> args) {
        String patternStr = (String) args.get("pattern");
        String searchPath = (String) args.get("path");
        String include = (String) args.get("include");
        boolean caseInsensitive = Boolean.TRUE.equals(args.get("case_insensitive"));

        if (patternStr == null || patternStr.isBlank()) {
            return "Error: pattern is required";
        }

        Path searchDir = (searchPath != null && !searchPath.isBlank())
                ? resolvePath(searchPath)
                : workDir;

        int flags = caseInsensitive ? Pattern.CASE_INSENSITIVE : 0;
        Pattern regex;
        try {
            regex = Pattern.compile(patternStr, flags);
        } catch (Exception e) {
            return "Error: invalid regex: " + e.getMessage();
        }

        PathMatcher includeMatcher = (include != null && !include.isBlank())
                ? FileSystems.getDefault().getPathMatcher("glob:" + include)
                : null;

        List<String> results = new ArrayList<>();
        int maxResults = 200;

        try {
            if (Files.isRegularFile(searchDir)) {
                searchFile(searchDir, regex, results, maxResults, searchDir.getParent());
            } else {
                Files.walkFileTree(searchDir, EnumSet.of(FileVisitOption.FOLLOW_LINKS), 20, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, java.nio.file.attribute.BasicFileAttributes attrs) {
                        if (results.size() >= maxResults) return FileVisitResult.TERMINATE;

                        if (includeMatcher != null && !includeMatcher.matches(file.getFileName())) {
                            return FileVisitResult.CONTINUE;
                        }

                        if (isBinaryOrLargeFile(file, attrs)) {
                            return FileVisitResult.CONTINUE;
                        }

                        searchFile(file, regex, results, maxResults, searchDir);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                        if (results.size() >= maxResults) return FileVisitResult.TERMINATE;
                        String name = dir.getFileName() != null ? dir.getFileName().toString() : "";
                        if (name.equals(".git") || name.equals("node_modules") || name.equals("target") || name.equals(".idea")) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path file, IOException exc) {
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
        } catch (IOException e) {
            return "Error searching: " + e.getMessage();
        }

        if (results.isEmpty()) {
            return "No matches found for pattern: " + patternStr;
        }
        String output = String.join("\n", results);
        if (results.size() >= maxResults) {
            output += "\n... [results truncated at " + maxResults + " matches]";
        }
        return output;
    }

    private void searchFile(Path file, Pattern regex, List<String> results, int maxResults, Path baseDir) {
        try {
            List<String> lines = Files.readAllLines(file);
            Path relative = baseDir.relativize(file);
            for (int i = 0; i < lines.size() && results.size() < maxResults; i++) {
                if (regex.matcher(lines.get(i)).find()) {
                    results.add(relative + ":" + (i + 1) + ": " + lines.get(i).trim());
                }
            }
        } catch (Exception ignored) {
            // skip files that can't be read as text
        }
    }

    private boolean isBinaryOrLargeFile(Path file, java.nio.file.attribute.BasicFileAttributes attrs) {
        if (attrs.size() > 1_000_000) return true; // skip files > 1MB
        String name = file.getFileName().toString().toLowerCase();
        return name.endsWith(".jar") || name.endsWith(".class") || name.endsWith(".png")
                || name.endsWith(".jpg") || name.endsWith(".gif") || name.endsWith(".ico")
                || name.endsWith(".zip") || name.endsWith(".gz") || name.endsWith(".tar")
                || name.endsWith(".pdf") || name.endsWith(".bin") || name.endsWith(".exe")
                || name.endsWith(".so") || name.endsWith(".dylib") || name.endsWith(".dll");
    }

    private Path resolvePath(String filePath) {
        Path p = Paths.get(filePath);
        if (p.isAbsolute()) return p;
        return workDir.resolve(p);
    }
}
