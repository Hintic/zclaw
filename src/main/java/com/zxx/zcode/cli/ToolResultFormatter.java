package com.zxx.zcode.cli;

import com.zxx.zcode.llm.model.ToolCallInfo;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Formats tool execution results for user-friendly display in the terminal.
 */
public class ToolResultFormatter {

    // ANSI colors
    private static final String CYAN = "\u001B[36m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RED = "\u001B[31m";
    private static final String GRAY = "\u001B[90m";
    private static final String BOLD = "\u001B[1m";
    private static final String RESET = "\u001B[0m";

    /**
     * Format a tool execution result for display.
     *
     * @param toolName the name of the tool
     * @param args     the tool arguments as JSON string (used for extracting context)
     * @param result   the raw tool execution result
     * @return formatted string for display, or null if result should not be shown
     */
    public String format(String toolName, String args, String result) {
        return switch (toolName) {
            case "bash" -> formatBash(result);
            case "read_file" -> formatReadFile(result, args);
            case "write_file" -> formatWriteFile(result, args);
            case "edit_file" -> formatEditFile(result, args);
            case "glob" -> formatGlob(result);
            case "grep" -> formatGrep(result);
            case "task_plan" -> formatTaskPlan(result);
            case "soul_mail" -> formatSoulMail(result);
            case "soul_mood" -> formatSoulMood(result);
            case "browser" -> formatBrowser(result);
            default -> formatDefault(toolName, result);
        };
    }

    private String formatBash(String result) {
        if (result == null || result.isEmpty()) {
            return GRAY + "  (no output)" + RESET;
        }

        // Check for error exit code
        boolean isError = result.contains("[Exit code:") && !result.contains("[Exit code: 0]");

        List<String> lines = result.lines().toList();
        int lineCount = lines.size();

        StringBuilder sb = new StringBuilder();
        sb.append(GRAY).append("  ");
        if (isError) {
            sb.append(RED);
        }
        sb.append("✓ ").append(lineCount).append(" line").append(lineCount != 1 ? "s" : "").append(RESET);

        if (lineCount > 20) {
            sb.append("\n");
            // First 10 lines
            for (int i = 0; i < 10; i++) {
                sb.append(GRAY).append("  ").append(lines.get(i)).append(RESET).append("\n");
            }
            sb.append(GRAY).append("  ... ").append(lineCount - 15).append(" more lines ...\n").append(RESET);
            // Last 5 lines
            for (int i = lineCount - 5; i < lineCount; i++) {
                sb.append(GRAY).append("  ").append(lines.get(i)).append(RESET).append("\n");
            }
        } else {
            // Show all lines, indented
            for (String line : lines) {
                sb.append("\n").append(GRAY).append("  ").append(line).append(RESET);
            }
        }

        if (isError) {
            sb.append(RED).append("  [Error]").append(RESET);
        }

        return sb.toString();
    }

    private String formatReadFile(String result, String args) {
        if (result == null || result.isEmpty()) {
            return GRAY + "  (empty file)" + RESET;
        }

        List<String> lines = result.lines().toList();
        int lineCount = lines.size();

        StringBuilder sb = new StringBuilder();
        sb.append(GRAY).append("  ✓ ").append(lineCount).append(" line").append(lineCount != 1 ? "s" : "").append(RESET);

        if (lineCount > 50) {
            sb.append(GRAY).append(" (showing 1-20, ").append(lineCount - 30).append(" more lines hidden)").append(RESET);
            sb.append("\n");
            // First 20 lines
            for (int i = 0; i < 20; i++) {
                sb.append(GRAY).append("  ").append(lines.get(i)).append(RESET).append("\n");
            }
            sb.append(GRAY).append("  ...\n").append(RESET);
            // Last 10 lines
            for (int i = lineCount - 10; i < lineCount; i++) {
                sb.append(GRAY).append("  ").append(lines.get(i)).append(RESET).append("\n");
            }
        } else {
            // Show all lines, indented
            for (String line : lines) {
                sb.append("\n").append(GRAY).append("  ").append(line).append(RESET);
            }
        }

        return sb.toString();
    }

    private String formatWriteFile(String result, String args) {
        // Result like: "File written successfully: /path/to/file (1234 chars)"
        if (result == null || result.isEmpty()) {
            return GRAY + "  (no result)" + RESET;
        }
        return GRAY + "  ✓ " + result + RESET;
    }

    private String formatEditFile(String result, String args) {
        // Result like: "File edited: /path/to/file\n\n--- a/path..."
        // or: "Error: old_string not found in file"
        if (result == null || result.isEmpty()) {
            return GRAY + "  (no result)" + RESET;
        }

        if (result.startsWith("Error:")) {
            return RED + "  ✗ " + result + RESET;
        }

        if (!result.startsWith("File edited:")) {
            return GRAY + "  " + result + RESET;
        }

        // Extract file path from first line
        String firstLine = result.split("\n")[0];
        String filePath = firstLine.replace("File edited: ", "");

        // The diff is on subsequent lines
        String diffSection = result.contains("\n") ? result.substring(firstLine.length()).trim() : "";

        StringBuilder sb = new StringBuilder();
        sb.append(GREEN).append("  ✓ ").append(RESET);
        sb.append(GREEN).append(filePath).append(RESET);

        // Show diff if present
        if (!diffSection.isEmpty()) {
            sb.append("\n").append(diffSection);
        }

        return sb.toString();
    }

    private String formatGlob(String result) {
        if (result == null || result.isEmpty()) {
            return GRAY + "  (no result)" + RESET;
        }

        // Result format: "X files matched:\nfile1\nfile2\n..."
        String[] parts = result.split("\n", 2);
        if (parts.length == 0) return GRAY + "  (no result)" + RESET;

        String firstLine = parts[0];
        if (!firstLine.contains("files matched") && !firstLine.contains("No files matched")) {
            return GRAY + "  " + result + RESET;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(GREEN).append("  ✓ ").append(RESET).append(firstLine).append("\n");

        if (parts.length > 1) {
            List<String> files = List.of(parts[1].split("\n"));
            int fileCount = files.size();

            if (fileCount > 15) {
                for (int i = 0; i < 10; i++) {
                    sb.append(GRAY).append("  ").append(files.get(i)).append(RESET).append("\n");
                }
                sb.append(GRAY).append("  ... ").append(fileCount - 15).append(" more files ...").append(RESET);
            } else {
                for (String file : files) {
                    sb.append(GRAY).append("  ").append(file).append(RESET).append("\n");
                }
            }
        }

        return sb.toString().trim();
    }

    private String formatGrep(String result) {
        if (result == null || result.isEmpty()) {
            return GRAY + "  (no result)" + RESET;
        }

        // Check for "No matches found"
        if (result.startsWith("No matches found")) {
            return YELLOW + "  ○ " + result + RESET;
        }

        List<String> lines = result.lines().toList();
        int matchCount = lines.size();

        StringBuilder sb = new StringBuilder();
        sb.append(GREEN).append("  ✓ ").append(matchCount).append(" match").append(matchCount != 1 ? "es" : "").append(RESET);

        if (matchCount > 30) {
            sb.append(GRAY).append(" (showing 1-15, more hidden)").append(RESET).append("\n");
            for (int i = 0; i < 15; i++) {
                sb.append(GRAY).append("  ").append(lines.get(i)).append(RESET).append("\n");
            }
            sb.append(GRAY).append("  ... ").append(matchCount - 30).append(" more matches ...").append(RESET);
        } else {
            sb.append("\n");
            for (String line : lines) {
                sb.append(GRAY).append("  ").append(line).append(RESET).append("\n");
            }
        }

        return sb.toString().trim();
    }

    /**
     * Mandatory visual task board for task_plan tool output.
     */
    private String formatTaskPlan(String result) {
        if (result == null || result.isEmpty()) {
            return GRAY + "  (no result)" + RESET;
        }
        if (!result.contains("ZCODE_TASK_PLAN_V1") || !result.contains("ZCODE_TASK_PLAN_END")) {
            if (result.startsWith("Error:")) {
                return RED + "  " + result + RESET;
            }
            return GRAY + "  " + result + RESET;
        }
        String goal = "";
        List<String> lines = new java.util.ArrayList<>();
        boolean inBlock = false;
        for (String line : result.lines().toList()) {
            if (line.startsWith("ZCODE_TASK_PLAN_V1")) {
                inBlock = true;
                continue;
            }
            if (line.startsWith("ZCODE_TASK_PLAN_END")) {
                break;
            }
            if (!inBlock) {
                continue;
            }
            if (line.startsWith("goal: ")) {
                goal = line.substring("goal: ".length()).trim();
                continue;
            }
            if (line.startsWith("step: ")) {
                lines.add(line);
            }
        }

        int w = Math.min(56, Math.max(32, Math.max(goal.length() + 8, 40)));
        String bar = "─".repeat(w);
        StringBuilder sb = new StringBuilder();
        sb.append(CYAN).append("  ┌").append(bar).append("┐").append(RESET).append('\n');
        sb.append(CYAN).append("  │ ").append(BOLD).append("TASK BOARD").append(RESET).append(CYAN)
                .append(" ".repeat(Math.max(0, w - 11))).append("│").append(RESET).append('\n');
        sb.append(CYAN).append("  ├").append(bar).append("┤").append(RESET).append('\n');
        if (!goal.isEmpty() && !"(no goal set)".equals(goal)) {
            int maxGoal = Math.max(8, w - 12);
            String g = goal.length() > maxGoal ? goal.substring(0, maxGoal) + "…" : goal;
            sb.append(CYAN).append("  │ ").append(RESET).append(GRAY).append("Goal: ").append(RESET)
                    .append(g).append('\n');
            sb.append(CYAN).append("  ├").append(bar).append("┤").append(RESET).append('\n');
        }
        if (lines.isEmpty()) {
            sb.append(CYAN).append("  │ ").append(RESET).append(GRAY).append("(no steps)").append(RESET).append('\n');
        } else {
            for (String raw : lines) {
                ParsedStep ps = parseStepLine(raw);
                String icon = switch (ps.status) {
                    case "done" -> GREEN + " ✓ " + RESET;
                    case "in_progress" -> YELLOW + " ▶ " + RESET;
                    case "blocked" -> RED + " ⨯ " + RESET;
                    default -> GRAY + " ○ " + RESET;
                };
                String row = ps.index + "." + icon + ps.title;
                if (ps.note != null && !ps.note.isEmpty()) {
                    row += GRAY + "  — " + ps.note + RESET;
                }
                sb.append(CYAN).append("  │ ").append(RESET).append(row).append('\n');
            }
        }
        sb.append(CYAN).append("  └").append(bar).append("┘").append(RESET);
        return sb.toString();
    }

    private String formatSoulMood(String result) {
        if (result == null || result.isEmpty()) {
            return GRAY + "  (no result)" + RESET;
        }
        if (result.startsWith("Error:") || result.startsWith("soul_mood is only")) {
            return GRAY + "  " + result + RESET;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(YELLOW).append("  ◆ mood").append(RESET).append('\n');
        for (String line : result.lines().toList()) {
            sb.append(GRAY).append("  ").append(line).append(RESET).append('\n');
        }
        return sb.toString().trim();
    }

    private String formatBrowser(String result) {
        if (result == null || result.isEmpty()) {
            return GRAY + "  (no result)" + RESET;
        }
        if (result.startsWith("Error:")) {
            return RED + "  " + result + RESET;
        }
        int previewLines = 12;
        List<String> lines = result.lines().toList();
        StringBuilder sb = new StringBuilder();
        sb.append(CYAN).append("  browser").append(RESET).append('\n');
        int shown = Math.min(lines.size(), previewLines);
        for (int i = 0; i < shown; i++) {
            sb.append(GRAY).append("  ").append(lines.get(i)).append(RESET).append('\n');
        }
        if (lines.size() > previewLines) {
            sb.append(GRAY).append("  ... ").append(lines.size() - previewLines).append(" more lines")
                    .append(RESET).append('\n');
        }
        return sb.toString().trim();
    }

    private String formatSoulMail(String result) {
        if (result == null || result.isEmpty()) {
            return GRAY + "  (no result)" + RESET;
        }
        if (result.startsWith("Error:")) {
            return RED + "  " + result + RESET;
        }
        if (result.startsWith("Sent to soul")) {
            return CYAN + "  ✉  " + RESET + GRAY + result + RESET;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(CYAN).append("  ✉  peer mail").append(RESET).append('\n');
        for (String line : result.lines().toList()) {
            sb.append(GRAY).append("  ").append(line).append(RESET).append('\n');
        }
        return sb.toString().trim();
    }

    private static final class ParsedStep {
        int index;
        String status = "pending";
        String title = "";
        String note = "";
    }

    private static ParsedStep parseStepLine(String line) {
        ParsedStep p = new ParsedStep();
        if (!line.startsWith("step: ")) {
            return p;
        }
        String rest = line.substring("step: ".length()).trim();
        String[] parts = rest.split("\\|");
        for (String part : parts) {
            part = part.trim();
            if (part.isEmpty()) {
                continue;
            }
            if (!part.contains("=")) {
                try {
                    p.index = Integer.parseInt(part);
                } catch (NumberFormatException ignored) {
                    // ignore
                }
                continue;
            }
            if (part.startsWith("status=")) {
                p.status = part.substring("status=".length()).trim().toLowerCase();
            } else if (part.startsWith("title=")) {
                p.title = part.substring("title=".length()).trim();
            } else if (part.startsWith("note=")) {
                p.note = part.substring("note=".length()).trim();
            }
        }
        return p;
    }

    private String formatDefault(String toolName, String result) {
        if (result == null || result.isEmpty()) {
            return GRAY + "  (no result)" + RESET;
        }
        // Truncate long results
        if (result.length() > 500) {
            return GRAY + "  " + result.substring(0, 500) + "\n  ... (" + (result.length() - 500) + " more chars) ..." + RESET;
        }
        return GRAY + "  " + result + RESET;
    }

    private String extractFilePath(String result, String prefix) {
        if (result.startsWith(prefix)) {
            String rest = result.substring(prefix.length());
            int parenIdx = rest.indexOf(" (");
            if (parenIdx > 0) {
                return rest.substring(0, parenIdx);
            }
            return rest;
        }
        return result;
    }

    private String computeDiffFromArgs(String args) {
        // Args is JSON like: {"file_path": "...", "old_string": "...", "new_string": "..."}
        // We need to count newlines in old_string and new_string
        try {
            // Simple approach: extract old_string and new_string by finding them in the JSON
            // This is a bit fragile but works for the common case
            int oldStart = args.indexOf("\"old_string\"");
            int newStart = args.indexOf("\"new_string\"");

            if (oldStart < 0 || newStart < 0) return "";

            // Extract old_string value
            int oldColon = args.indexOf(":", oldStart);
            int oldQuote1 = args.indexOf("\"", oldColon);
            int oldQuote2 = args.indexOf("\"", oldQuote1 + 1);
            String oldStr = args.substring(oldQuote1 + 1, oldQuote2);

            // Extract new_string value
            int newColon = args.indexOf(":", newStart);
            int newQuote1 = args.indexOf("\"", newColon);
            int newQuote2 = args.indexOf("\"", newQuote1 + 1);
            String newStr = args.substring(newQuote1 + 1, newQuote2);

            int oldLines = countLines(oldStr);
            int newLines = countLines(newStr);

            if (oldLines == 0 && newLines == 0) {
                return "+" + newLines + " -" + oldLines + " chars";
            }

            return "+" + newLines + " -" + oldLines + " line" + (oldLines != 1 ? "s" : "");
        } catch (Exception e) {
            return "";
        }
    }

    private int countLines(String str) {
        if (str == null || str.isEmpty()) return 0;
        int count = 1;
        for (int i = 0; i < str.length(); i++) {
            if (str.charAt(i) == '\n') count++;
            // Handle escaped newlines in JSON strings (\n)
            if (str.charAt(i) == '\\' && i + 1 < str.length() && str.charAt(i + 1) == 'n') {
                count++;
                i++; // skip the 'n'
            }
        }
        return count;
    }
}
