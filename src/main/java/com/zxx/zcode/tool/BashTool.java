package com.zxx.zcode.tool;

import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class BashTool implements Tool {

    private final Path workDir;

    public BashTool(Path workDir) {
        this.workDir = workDir;
    }

    @Override
    public String name() { return "bash"; }

    @Override
    public String description() {
        return "Execute a bash command in the working directory. " +
                "Use for running builds, tests, git commands, and other shell operations. " +
                "Commands time out after 120 seconds.";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("command", Map.of("type", "string", "description", "The bash command to execute"));
        props.put("timeout", Map.of("type", "integer", "description", "Timeout in seconds (default: 120)"));

        return Map.of(
                "type", "object",
                "properties", props,
                "required", List.of("command")
        );
    }

    @Override
    public String execute(Map<String, Object> args) {
        String command = (String) args.get("command");
        int timeout = 120;
        if (args.get("timeout") instanceof Number) {
            timeout = ((Number) args.get("timeout")).intValue();
        }

        if (command == null || command.isBlank()) {
            return "Error: command is required";
        }

        try {
            ProcessBuilder pb = new ProcessBuilder("bash", "-c", command);
            pb.directory(workDir.toFile());
            pb.redirectErrorStream(true);

            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(timeout, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return output + "\n[Process timed out after " + timeout + "s and was killed]";
            }

            int exitCode = process.exitValue();
            String result = output.toString();
            if (exitCode != 0) {
                result += "\n[Exit code: " + exitCode + "]";
            }
            return result.isEmpty() ? "(no output)" : result;
        } catch (Exception e) {
            return "Error executing command: " + e.getMessage();
        }
    }
}
