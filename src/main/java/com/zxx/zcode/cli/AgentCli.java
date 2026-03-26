package com.zxx.zcode.cli;

import com.zxx.zcode.agent.AgentLoop;
import com.zxx.zcode.agent.ConversationContextEstimator;
import com.zxx.zcode.skill.SkillService;
import com.zxx.zcode.soul.SoulMoodStore;
import com.zxx.zcode.soul.SoulProfile;
import org.jline.reader.*;
import org.jline.reader.impl.DefaultParser;
import org.jline.reader.impl.completer.ArgumentCompleter;
import org.jline.reader.impl.completer.StringsCompleter;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.IOException;
import java.util.List;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Locale;

/**
 * JLine-based REPL for interactive agent sessions.
 */
public class AgentCli {

    private static final String DIM = "\u001B[90m";
    private static final String GREEN = "\u001B[32m";
    private static final String CYAN = "\u001B[36m";
    private static final String BOLD = "\u001B[1m";
    private static final String RED = "\u001B[31m";
    private static final String RESET = "\u001B[0m";

    private final AgentLoop agentLoop;
    private final Path workDir;
    private final String modelName;
    private final SoulProfile soul;
    private final PrintStream out;
    private final int soulMailPollSeconds;
    private boolean isolatedMode = false;

    /** Pixel-face column width (monospace cells). */
    private static final int WELCOME_FACE_COLS = 14;

    /** 7-row pixel face (block + light shade). */
    private static final String[] WELCOME_PIXEL_FACE = {
            "     ██     ",
            "   ██████   ",
            "  ██░░░░██  ",
            "  █░█  █░█  ",
            "  █░░██░░█  ",
            "   ██████   ",
            "    ▀▀▀▀    ",
    };

    public AgentCli(
            AgentLoop agentLoop,
            Path workDir,
            String modelName,
            SoulProfile soul,
            PrintStream out,
            int soulMailPollSeconds) {
        this.agentLoop = agentLoop;
        this.workDir = workDir;
        this.modelName = modelName;
        this.soul = soul != null ? soul : SoulProfile.defaultProfile();
        this.out = out;
        this.soulMailPollSeconds = Math.max(0, soulMailPollSeconds);
    }

    public void run() {
        try (Terminal terminal = TerminalBuilder.builder()
                .system(true)
                .build()) {

            List<Completer> completors = List.of(
                    new StringsCompleter("/help", "/clear", "/exit", "/quit", "/q",
                            "/status", "/null", "/notnull")
            );

            LineReader reader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .parser(new DefaultParser())
                    .variable(LineReader.SECONDARY_PROMPT_PATTERN, DIM + "│" + RESET + "  ... ")
                    .completer(new ArgumentCompleter(completors))
                    .build();

            printWelcome(terminal);
            final Object agentInvokeLock = new Object();
            SoulMailPollScheduler mailPoll = SoulMailPollScheduler.maybeStart(
                    workDir,
                    soul.getId(),
                    soulMailPollSeconds,
                    agentLoop,
                    out,
                    () -> isolatedMode,
                    agentInvokeLock,
                    reader);
            if (mailPoll != null) {
                out.println(DIM + "  Soul mail auto-poll: every " + soulMailPollSeconds
                        + "s (shared inbox under this work dir)" + RESET);
                out.println();
            }

            // Prompt shown after the pre-drawn left border in the input frame.
            String inputPrompt = "  " + GREEN + ">" + RESET + " ";

            try {
                while (true) {
                    drawInputBoxFrame(terminal);

                    String line;
                    try {
                        line = reader.readLine(inputPrompt);
                    } catch (UserInterruptException e) {
                        moveCursorBelowInputBox(terminal);
                        out.println();
                        continue;
                    } catch (EndOfFileException e) {
                        moveCursorBelowInputBox(terminal);
                        out.println();
                        CliIndent.printlnIndented(out, "Bye!");
                        break;
                    }

                    moveCursorBelowInputBox(terminal);

                    if (line == null || line.isBlank()) continue;

                    String input = line.trim();

                    // Slash commands
                    if (input.startsWith("/")) {
                        if (!handleSlashCommand(input)) break;
                        continue;
                    }

                    // Process through agent loop
                    out.println();
                    out.println("  " + BOLD + "✻ z-code" + RESET);
                    if (isolatedMode) {
                        out.println("  [isolated mode] " + DIM + "(tools disabled)" + RESET);
                    }
                    out.println();
                    try {
                        synchronized (agentInvokeLock) {
                            if (isolatedMode) {
                                agentLoop.processIsolated(input);
                            } else {
                                agentLoop.processInput(input);
                            }
                        }
                    } catch (IOException e) {
                        CliIndent.printlnIndented(out, RED + "Error: " + e.getMessage() + RESET);
                    }
                    out.println();
                }
            } finally {
                if (mailPoll != null) {
                    mailPoll.close();
                }
            }
        } catch (IOException e) {
            CliIndent.printlnIndented(out, "Fatal: failed to initialize terminal: " + e.getMessage());
        }
    }

    private int termWidth(Terminal terminal) {
        return Math.max(terminal.getWidth(), 40);
    }

    /**
     * Draw a full 3-line frame before each read:
     *   ╭────
     *   │  > ...
     *   ╰────
     * Cursor is placed at the middle line start; JLine then renders the prompt there.
     */
    private void drawInputBoxFrame(Terminal terminal) {
        int cols = termWidth(terminal);
        String horiz = "─".repeat(Math.max(1, cols - 1));
        var w = terminal.writer();
        w.print(DIM);
        w.print("╭");
        w.print(horiz);
        w.print(RESET);
        w.println();
        w.print(DIM);
        w.print("│");
        w.print(RESET);
        w.println();
        w.print(DIM);
        w.print("╰");
        w.print(horiz);
        w.print(RESET);
        // Move back up one row and to column 1 so readLine writes inside the frame.
        w.print("\u001B[1A\r");
        w.flush();
        terminal.flush();
    }

    /**
     * After readLine returns (or is interrupted), place cursor below the frame.
     */
    private void moveCursorBelowInputBox(Terminal terminal) {
        var w = terminal.writer();
        w.flush();
        w.println();
        w.println();
        terminal.flush();
    }

    /**
     * @return true to continue the REPL, false to exit
     */
    private boolean handleSlashCommand(String input) {
        String cmd = input.toLowerCase();
        switch (cmd) {
            case "/exit", "/quit", "/q" -> {
                CliIndent.printlnIndented(out, "Bye!");
                return false;
            }
            case "/clear" -> {
                agentLoop.clearHistory();
                CliIndent.printlnIndented(out, "Conversation cleared.");
                return true;
            }
            case "/help" -> {
                printHelp();
                return true;
            }
            case "/status" -> {
                CliIndent.printlnIndented(out, "Soul: " + soul.getDisplayName() + " (`" + soul.getId() + "`)");
                if (!soul.isDefault()) {
                    try {
                        SoulMoodStore st = new SoulMoodStore(workDir);
                        int score = st.load(soul.getId()).score;
                        CliIndent.printlnIndented(out, "Mood score: " + score + "/100");
                    } catch (java.io.IOException e) {
                        CliIndent.printlnIndented(out, "Mood: (unreadable)");
                    }
                }
                CliIndent.printlnIndented(out, "Messages in history: " + agentLoop.messageCount());
                CliIndent.printlnIndented(out, SkillService.statusLine(workDir, soul));
                AgentLoop.ConversationContextSnapshot ctx = agentLoop.getConversationContextSnapshot();
                CliIndent.printlnIndented(out, "Context (est., next request, ~"
                        + ConversationContextEstimator.CHARS_PER_TOKEN_APPROX + " chars/token): ~"
                        + String.format(Locale.US, "%,d", ctx.approxContextTokens()) + " tokens (~"
                        + String.format(Locale.US, "%,d", ctx.approxContextChars()) + " chars), "
                        + ctx.messagesForNextRequest() + " message(s) incl. system; rolling cap "
                        + ctx.maxNonSystemMessagesCap() + " non-system"
                        + ctx.referencePercentSuffix());
                AgentLoop.SessionUsageTotals usage = agentLoop.getSessionUsageTotals();
                if (usage.totalTokens() > 0 || usage.cacheReadTokens() > 0 || usage.cacheCreationTokens() > 0) {
                    CliIndent.printlnIndented(out, "Session tokens (cumulative): " + usage.promptTokens()
                            + " prompt in / " + usage.completionTokens() + " completion out (total "
                            + usage.totalTokens() + ")");
                    String cacheExtra = usage.cacheCreationTokens() > 0
                            ? "; cache creation " + usage.cacheCreationTokens() + " (Anthropic)"
                            : "";
                    CliIndent.printlnIndented(out, "Prompt cache (cumulative): " + usage.cacheReadTokens()
                            + " read" + cacheExtra + "; hit rate " + usage.cacheHitRatePercentOrDash());
                } else {
                    CliIndent.printlnIndented(out, "Session tokens (cumulative): none recorded yet");
                }
                CliIndent.printlnIndented(out, "Working directory: " + workDir.toAbsolutePath());
                CliIndent.printlnIndented(out, "Isolated mode: " + (isolatedMode ? "ON (tools disabled)" : "OFF"));
                if (soulMailPollSeconds > 0) {
                    CliIndent.printlnIndented(out, "Soul mail auto-poll: every " + soulMailPollSeconds + "s");
                } else {
                    CliIndent.printlnIndented(out, "Soul mail auto-poll: off");
                }
                return true;
            }
            case "/null" -> {
                isolatedMode = true;
                CliIndent.printlnIndented(out, GREEN + "Entered isolated mode. Tools disabled. Use /notnull to exit." + RESET);
                return true;
            }
            case "/notnull" -> {
                isolatedMode = false;
                CliIndent.printlnIndented(out, GREEN + "Exited isolated mode. Tools re-enabled." + RESET);
                return true;
            }
            default -> {
                CliIndent.printlnIndented(out, "Unknown command: " + input + ". Type /help for available commands.");
                return true;
            }
        }
    }

    private void printWelcome(Terminal terminal) {
        String home = System.getProperty("user.home");
        String dir = workDir.toAbsolutePath().toString();
        if (home != null && dir.startsWith(home)) {
            dir = "~" + dir.substring(home.length());
        }

        String line1Plain = "  ✻ z-code v1.0";
        String line1Row = BOLD + line1Plain + RESET
                + (!soul.isDefault() ? DIM + "  ·  " + soul.getDisplayName() + " (`" + soul.getId() + "`)" + RESET : "");
        String line2 = "  Model: " + modelName;
        String line3 = "  Work dir: " + dir;
        String line4;
        if (!soul.isDefault() && !soul.getPersona().isBlank()) {
            line4 = GREEN + "  Soul persona loaded: " + soul.getId() + RESET;
        } else if (!soul.isDefault()) {
            line4 = DIM + "  No soul JSON — add .zcode/souls/" + soul.getId()
                    + "/soul.json (or flat .zcode/souls/" + soul.getId() + ".json; ~/.zcode/…)" + RESET;
        } else {
            line4 = "  Type /help for commands";
        }
        String line5 = "  Type /help for commands";

        int line1ApproxLen = line1Plain.length() + (!soul.isDefault()
                ? 8 + soul.getDisplayName().length() + soul.getId().length() : 0);
        int lowerBlockW = Math.max(Math.max(line2.length(), line3.length()),
                Math.max(visibleLength(line4), line5.length()));
        int textContentW = Math.max(Math.max(line1ApproxLen, line2.length()), lowerBlockW) + 4;
        int width = Math.max(textContentW + WELCOME_FACE_COLS, termWidth(terminal) - 2);
        int textCols = width - WELCOME_FACE_COLS;
        String[] textRows = {
                line1Row, "",
                line2, line3,
                line4, line5,
                "",
        };

        out.println();
        out.println(CYAN + "╭" + "─".repeat(width) + "╮" + RESET);
        for (int r = 0; r < WELCOME_PIXEL_FACE.length; r++) {
            String facePlain = padRight(WELCOME_PIXEL_FACE[r], WELCOME_FACE_COLS);
            String faceStyled = stylizeWelcomePixelLine(facePlain);
            String textCell = r < textRows.length ? textRows[r] : "";
            String textPadded = padRightVisible(textCell, textCols);
            out.println(CYAN + "│" + RESET + faceStyled + textPadded + CYAN + "│" + RESET);
        }
        out.println(CYAN + "╰" + "─".repeat(width) + "╯" + RESET);
        out.println();
    }

    private String stylizeWelcomePixelLine(String line) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            switch (c) {
                case '█', '▀', '▄' -> sb.append(CYAN).append(c).append(RESET);
                case '░' -> sb.append(DIM).append(c).append(RESET);
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Pad to {@code width} visible cells (ANSI sequences do not count toward width).
     */
    private String padRightVisible(String s, int width) {
        if (s == null) {
            s = "";
        }
        int vis = visibleLength(s);
        if (vis >= width) {
            return truncateToVisible(s, width);
        }
        return s + " ".repeat(width - vis);
    }

    private static int visibleLength(String s) {
        int n = 0;
        int i = 0;
        while (i < s.length()) {
            if (s.charAt(i) == '\u001B' && i + 1 < s.length() && s.charAt(i + 1) == '[') {
                i += 2;
                while (i < s.length() && s.charAt(i) != 'm') {
                    i++;
                }
                if (i < s.length()) {
                    i++;
                }
                continue;
            }
            n++;
            i++;
        }
        return n;
    }

    private static String truncateToVisible(String s, int maxVis) {
        StringBuilder out = new StringBuilder();
        int vis = 0;
        int i = 0;
        while (i < s.length()) {
            if (s.charAt(i) == '\u001B' && i + 1 < s.length() && s.charAt(i + 1) == '[') {
                int escStart = i;
                i += 2;
                while (i < s.length() && s.charAt(i) != 'm') {
                    i++;
                }
                if (i < s.length()) {
                    i++;
                }
                out.append(s, escStart, i);
                continue;
            }
            if (vis >= maxVis) {
                break;
            }
            out.append(s.charAt(i));
            vis++;
            i++;
        }
        return out.toString();
    }

    private String padRight(String s, int width) {
        if (s.length() >= width) return s;
        return s + " ".repeat(width - s.length());
    }

    private void printHelp() {
        out.println();
        CliIndent.printlnIndented(out, "Commands:");
        CliIndent.printlnIndented(out, "  /help    - Show this help");
        CliIndent.printlnIndented(out, "  /clear   - Clear conversation history");
        CliIndent.printlnIndented(out, "  /status  - Session info, usage, estimated context (ZCODE_CONTEXT_WINDOW for %)");
        CliIndent.printlnIndented(out, "  /null    - Enter isolated mode (tools disabled)");
        CliIndent.printlnIndented(out, "  /notnull - Exit isolated mode (tools enabled)");
        CliIndent.printlnIndented(out, "  /exit    - Exit z-code");
        out.println();
        CliIndent.printlnIndented(out, "Just type your question or task in natural language.");
        CliIndent.printlnIndented(out, "The agent can read/write files, run commands, and search code.");
        out.println();
        CliIndent.printlnIndented(out, "Long-term context is merged into memory.md (default soul: work dir root or");
        CliIndent.printlnIndented(out, ".zcode/souls/default/memory.md; named souls: .zcode/souls/<id>/memory.md).");
        CliIndent.printlnIndented(out, "(disable with --memory=false or ZCODE_MEMORY=false).");
        out.println();
        CliIndent.printlnIndented(out, "Souls: prefer <workDir>/.zcode/souls/<id>/soul.json (or flat <id>.json); ~/.zcode/souls/… as fallback.");
        CliIndent.printlnIndented(out, "Start with zcode <id>, --soul=<id>, --soul <id>, -soul=…, or ZCODE_SOUL.");
        CliIndent.printlnIndented(out, "Example gg/cc: copy examples/souls/<id>/ into .zcode/souls/ (soul.json + skills/).");
        CliIndent.printlnIndented(out, "Shared skills (all souls): .zcode/skills/<pack>/. Per-soul: .zcode/souls/<id>/skills/<pack>/ (see examples).");
        CliIndent.printlnIndented(out, "Peers see only skill metadata in .zcode/skill-manifests/<soul>.json; use soul_mail kind=skill_suggest to hand off.");
        CliIndent.printlnIndented(out, "Collaborate with soul_mail (task_report / review / skill_suggest); mood via soul_mood (see system prompt).");
        CliIndent.printlnIndented(out, "Named soul: inbox auto-poll every 10s unless disabled (see --soul-mail-poll=N,");
        CliIndent.printlnIndented(out, "ZCODE_SOUL_MAIL_POLL, soul_mail_poll_seconds in config.json; 0 = off). Paused in /null isolated mode.");
        out.println();
    }
}
