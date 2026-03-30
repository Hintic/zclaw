package com.zxx.zclaw.cli;

import java.io.PrintStream;

/**
 * Animated braille spinner for long-running work (LLM, tools, I/O).
 * Runs on a daemon thread; uses \r to overwrite the same line.
 */
public class ThinkingIndicator {

    private static final String[] FRAMES = {"⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"};
    private static final long INTERVAL_MS = 80;
    private static final int MAX_LABEL_LEN = 52;
    /** Visible prefix before label: two spaces + braille + space (must stay on one terminal row). */
    private static final int SPINNER_PREFIX_COLS = 4;
    private static final String CYAN = "\u001B[36m";
    private static final String RESET = "\u001B[0m";

    private final PrintStream out;
    private volatile boolean running;
    private Thread spinnerThread;

    public ThinkingIndicator(PrintStream out) {
        this.out = out;
    }

    /**
     * Start showing the spinner with the default "Thinking..." label.
     */
    public synchronized void start() {
        start("Thinking...");
    }

    /**
     * Start showing the spinner with a custom status (e.g. tool name + " running...").
     * Control characters are stripped; overly long text is truncated.
     */
    public synchronized void start(String statusLine) {
        if (running) return;
        final String label = sanitizeLabel(statusLine, resolveTermColumns());
        running = true;
        spinnerThread = new Thread(() -> {
            int i = 0;
            while (running) {
                String frame = FRAMES[i % FRAMES.length];
                // Single-row updates only: if the line wraps, \\r+EL cannot clear the wrapped rows (huge gap).
                out.print("\r\u001B[2K  " + CYAN + frame + RESET + " " + label);
                out.flush();
                i++;
                try {
                    Thread.sleep(INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            // Erase whole line (avoids guessing width; prevents leftover glyphs before streamed text)
            out.print("\r\u001B[2K");
            out.flush();
        }, "thinking-indicator");
        spinnerThread.setDaemon(true);
        spinnerThread.start();
    }

    /**
     * Best effort: COLUMNS from shell, else 80. Keeps spinner on one row so \\r+EL does not leave wrap garbage.
     */
    static int resolveTermColumns() {
        String c = System.getenv("COLUMNS");
        if (c != null) {
            try {
                int n = Integer.parseInt(c.trim());
                if (n >= 24) {
                    return n;
                }
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return 80;
    }

    static String sanitizeLabel(String raw) {
        return sanitizeLabel(raw, resolveTermColumns());
    }

    static String sanitizeLabel(String raw, int termColumns) {
        if (raw == null || raw.isBlank()) {
            return "Working...";
        }
        String s = raw.replace("\u001B", "").replace("\r", " ").replace("\n", " ").trim();
        if (s.isEmpty()) {
            return "Working...";
        }
        int reserve = SPINNER_PREFIX_COLS + 1;
        int maxLabel = Math.max(12, Math.min(MAX_LABEL_LEN, termColumns - reserve));
        if (s.length() > maxLabel) {
            s = s.substring(0, maxLabel - 3) + "...";
        }
        return s;
    }

    /**
     * Stop the spinner and wait for the line to be cleared.
     */
    public synchronized void stop() {
        if (!running) return;
        running = false;
        if (spinnerThread != null) {
            try {
                spinnerThread.join(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            spinnerThread = null;
        }
    }
}
