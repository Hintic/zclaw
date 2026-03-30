package com.zxx.zclaw.tool;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public class WriteFileTool implements Tool {

    private final Path workDir;

    public WriteFileTool(Path workDir) {
        this.workDir = workDir;
    }

    @Override
    public String name() { return "write_file"; }

    @Override
    public String description() {
        return "Write content to a file. Creates the file if it doesn't exist. " +
                "Creates parent directories as needed. Overwrites existing content.";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("file_path", Map.of("type", "string", "description", "Path to the file"));
        props.put("content", Map.of("type", "string", "description", "Content to write"));

        return Map.of(
                "type", "object",
                "properties", props,
                "required", List.of("file_path", "content")
        );
    }

    @Override
    public String execute(Map<String, Object> args) {
        String filePath = (String) args.get("file_path");
        String content = (String) args.get("content");

        if (content == null) {
            return "Error: content is required";
        }

        Path path = resolvePath(filePath);

        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            Files.writeString(path, content);
            return "File written successfully: " + path + " (" + content.length() + " chars)";
        } catch (IOException e) {
            return "Error writing file: " + e.getMessage();
        }
    }

    private Path resolvePath(String filePath) {
        Path p = Paths.get(filePath);
        if (p.isAbsolute()) return p;
        return workDir.resolve(p);
    }
}
