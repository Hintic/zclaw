package com.zxx.zcode.tool;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ReadFileTool implements Tool {

    private final Path workDir;

    public ReadFileTool(Path workDir) {
        this.workDir = workDir;
    }

    @Override
    public String name() { return "read_file"; }

    @Override
    public String description() {
        return "Read the contents of a file. Returns the file content with line numbers. " +
                "Use offset and limit to read specific portions of large files.";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("file_path", Map.of("type", "string", "description", "Path to the file (absolute or relative to work dir)"));
        props.put("offset", Map.of("type", "integer", "description", "Line number to start reading from (1-based, default 1)"));
        props.put("limit", Map.of("type", "integer", "description", "Number of lines to read (default: all)"));

        return Map.of(
                "type", "object",
                "properties", props,
                "required", List.of("file_path")
        );
    }

    @Override
    public String execute(Map<String, Object> args) {
        String filePath = (String) args.get("file_path");
        Path path = resolvePath(filePath);

        if (!Files.exists(path)) {
            return "Error: file not found: " + path;
        }
        if (Files.isDirectory(path)) {
            return "Error: path is a directory, not a file: " + path;
        }

        try {
            List<String> lines = Files.readAllLines(path);
            int offset = getInt(args, "offset", 1);
            int limit = getInt(args, "limit", lines.size());

            int startIdx = Math.max(0, offset - 1);
            int endIdx = Math.min(lines.size(), startIdx + limit);

            StringBuilder sb = new StringBuilder();
            for (int i = startIdx; i < endIdx; i++) {
                sb.append(String.format("%6d\t%s\n", i + 1, lines.get(i)));
            }
            if (sb.isEmpty()) {
                return "(empty file)";
            }
            return sb.toString();
        } catch (IOException e) {
            return "Error reading file: " + e.getMessage();
        }
    }

    private Path resolvePath(String filePath) {
        Path p = Paths.get(filePath);
        if (p.isAbsolute()) return p;
        return workDir.resolve(p);
    }

    private int getInt(Map<String, Object> args, String key, int defaultVal) {
        Object val = args.get(key);
        if (val instanceof Number) return ((Number) val).intValue();
        return defaultVal;
    }
}
