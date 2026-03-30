package com.zxx.zclaw.tool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DiffCalculator {

    private static final String RED_BG = "\u001b[41m";
    private static final String GREEN_BG = "\u001b[42m";
    private static final String WHITE_FG = "\u001b[97m";
    private static final String BLACK_FG = "\u001b[30m";
    private static final String RESET = "\u001b[0m";

    private final boolean useColor;

    public DiffCalculator() {
        this.useColor = isTTY();
    }

    public DiffCalculator(boolean useColor) {
        this.useColor = useColor;
    }

    public String generateUnifiedDiff(String oldContent, String newContent, String filePath) {
        if (oldContent.equals(newContent)) {
            return "";
        }

        String[] oldLines = oldContent.split("\n", -1);
        String[] newLines = newContent.split("\n", -1);

        List<DiffLine> diff = computeDiff(oldLines, newLines);

        if (diff.isEmpty()) {
            return "";
        }

        return formatUnifiedDiff(diff, filePath, oldLines.length, newLines.length);
    }

    private List<DiffLine> computeDiff(String[] oldLines, String[] newLines) {
        int m = oldLines.length;
        int n = newLines.length;

        int[][] dp = new int[m + 1][n + 1];
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                if (oldLines[i - 1].equals(newLines[j - 1])) {
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
                }
            }
        }

        List<DiffLine> diff = new ArrayList<>();
        int i = m, j = n;
        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && oldLines[i - 1].equals(newLines[j - 1])) {
                diff.add(new DiffLine(DiffType.CONTEXT, i - 1, j - 1, oldLines[i - 1]));
                i--;
                j--;
            } else if (j > 0 && (i == 0 || dp[i][j - 1] >= dp[i - 1][j])) {
                diff.add(new DiffLine(DiffType.ADD, -1, j - 1, newLines[j - 1]));
                j--;
            } else {
                diff.add(new DiffLine(DiffType.DELETE, i - 1, -1, oldLines[i - 1]));
                i--;
            }
        }
        Collections.reverse(diff);
        return diff;
    }

    private String formatUnifiedDiff(List<DiffLine> diff, String filePath, int oldLen, int newLen) {
        StringBuilder sb = new StringBuilder();
        sb.append("--- a/").append(filePath).append("\n");
        sb.append("+++ b/").append(filePath).append("\n");

        int oldLine = 1, newLine = 1;
        int contextStart = 0;
        boolean inHunk = false;

        for (int idx = 0; idx < diff.size(); idx++) {
            DiffLine line = diff.get(idx);

            if (line.type == DiffType.CONTEXT) {
                if (!inHunk) {
                    if (idx + 1 < diff.size() && diff.get(idx + 1).type != DiffType.CONTEXT) {
                        contextStart = idx;
                        inHunk = true;
                    }
                }
                oldLine++;
                newLine++;
            } else {
                if (inHunk) {
                    formatHunk(sb, diff, contextStart, idx, useColor);
                    oldLine = diff.get(idx).oldIndex + 1;
                    newLine = diff.get(idx).newIndex + 1;
                    inHunk = false;
                }

                if (line.type == DiffType.DELETE) {
                    sb.append("-").append(colorize(line.content, DiffType.DELETE));
                    oldLine = line.oldIndex + 1;
                } else if (line.type == DiffType.ADD) {
                    sb.append("+").append(colorize(line.content, DiffType.ADD));
                    newLine = line.newIndex + 1;
                }
                sb.append("\n");
            }
        }

        if (inHunk) {
            formatHunk(sb, diff, contextStart, diff.size(), useColor);
        }

        return sb.toString();
    }

    private void formatHunk(StringBuilder sb, List<DiffLine> diff, int start, int end, boolean color) {
        int oldStart = diff.get(start).oldIndex + 1;
        int newStart = diff.get(start).newIndex + 1;
        int oldCount = 0, newCount = 0;

        for (int i = start; i < end; i++) {
            DiffLine line = diff.get(i);
            if (line.type == DiffType.CONTEXT) {
                oldCount++;
                newCount++;
            } else if (line.type == DiffType.DELETE) {
                oldCount++;
            } else if (line.type == DiffType.ADD) {
                newCount++;
            }
        }

        sb.append("@@ -").append(oldStart).append(",").append(oldCount)
          .append(" +").append(newStart).append(",").append(newCount).append(" @@\n");

        for (int i = start; i < end; i++) {
            DiffLine line = diff.get(i);
            sb.append(" ").append(colorize(line.content, line.type)).append("\n");
        }
    }

    private String colorize(String content, DiffType type) {
        if (!useColor) {
            return content;
        }
        if (type == DiffType.DELETE) {
            return RED_BG + WHITE_FG + content + RESET;
        } else if (type == DiffType.ADD) {
            return GREEN_BG + BLACK_FG + content + RESET;
        }
        return content;
    }

    private static boolean isTTY() {
        String noColor = System.getenv("NO_COLOR");
        if (noColor != null && !noColor.isEmpty()) {
            return false;
        }
        return System.console() != null;
    }

    private enum DiffType {
        ADD, DELETE, CONTEXT
    }

    private static class DiffLine {
        final DiffType type;
        final int oldIndex;
        final int newIndex;
        final String content;

        DiffLine(DiffType type, int oldIndex, int newIndex, String content) {
            this.type = type;
            this.oldIndex = oldIndex;
            this.newIndex = newIndex;
            this.content = content;
        }
    }
}
