package com.zxx.zclaw.cli;

import java.io.PrintStream;

/**
 * Left margin for CLI messages so replies and tool output are not flush against the edge.
 */
public final class CliIndent {

    public static final String MARGIN = "  ";

    private CliIndent() {}

    /**
     * Prints each line of {@code text} with {@link #MARGIN}. Empty {@code text} is a no-op.
     * Uses {@code \\n} splitting with limit -1 so trailing blank lines are preserved.
     */
    public static void printlnIndented(PrintStream out, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        for (String line : text.split("\n", -1)) {
            out.println(MARGIN + line);
        }
    }
}
