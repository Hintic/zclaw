package com.zxx.zcode.tool;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public class EditFileTool implements Tool {

    private final Path workDir;
    private final DiffCalculator diffCalculator;

    public EditFileTool(Path workDir) {
        this.workDir = workDir;
        this.diffCalculator = new DiffCalculator();
    }

    EditFileTool(Path workDir, DiffCalculator diffCalculator) {
        this.workDir = workDir;
        this.diffCalculator = diffCalculator;
    }

    @Override
    public String name() { return "edit_file"; }

    @Override
    public String description() {
        return "Edit a file by replacing an exact string match. The old_string must appear exactly once " +
                "in the file (unless replace_all is true). Use this for surgical edits instead of rewriting entire files.";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("file_path", Map.of("type", "string", "description", "Path to the file to edit"));
        props.put("old_string", Map.of("type", "string", "description", "The exact string to find and replace"));
        props.put("new_string", Map.of("type", "string", "description", "The replacement string"));
        props.put("replace_all", Map.of("type", "boolean", "description", "Replace all occurrences (default: false)"));

        return Map.of(
                "type", "object",
                "properties", props,
                "required", List.of("file_path", "old_string", "new_string")
        );
    }

    @Override
    public String execute(Map<String, Object> args) {
        String filePath = (String) args.get("file_path");
        String oldString = (String) args.get("old_string");
        String newString = (String) args.get("new_string");
        boolean replaceAll = Boolean.TRUE.equals(args.get("replace_all"));

        if (oldString == null || newString == null) {
            return "Error: old_string and new_string are required";
        }

        Path path = resolvePath(filePath);

        if (!Files.exists(path)) {
            return "Error: file not found: " + path;
        }

        try {
            String content = Files.readString(path);

            String oldContent = Files.readString(path);

            if (!oldContent.contains(oldString)) {
                return "Error: old_string not found in file. Make sure it matches exactly (including whitespace/indentation).";
            }

            int firstIdx = oldContent.indexOf(oldString);
            int lastIdx = oldContent.lastIndexOf(oldString);
            if (!replaceAll && firstIdx != lastIdx) {
                int count = countOccurrences(oldContent, oldString);
                return "Error: old_string appears " + count + " times. Provide more context to make it unique, or use replace_all=true.";
            }

            String newContent;
            if (replaceAll) {
                newContent = oldContent.replace(oldString, newString);
            } else {
                newContent = oldContent.substring(0, firstIdx) + newString + oldContent.substring(firstIdx + oldString.length());
            }

            Files.writeString(path, newContent);

            String diff = diffCalculator.generateUnifiedDiff(oldContent, newContent, path.toString());
            StringBuilder result = new StringBuilder();
            result.append("File edited: ").append(path);
            if (!diff.isEmpty()) {
                result.append("\n\n").append(diff);
            }
            return result.toString();
        } catch (IOException e) {
            return "Error editing file: " + e.getMessage();
        }
    }

    private int countOccurrences(String text, String sub) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }

    private Path resolvePath(String filePath) {
        Path p = Paths.get(filePath);
        if (p.isAbsolute()) return p;
        return workDir.resolve(p);
    }
}
