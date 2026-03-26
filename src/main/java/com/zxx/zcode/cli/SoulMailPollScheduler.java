package com.zxx.zcode.cli;

import com.zxx.zcode.agent.AgentLoop;
import com.zxx.zcode.soul.SoulInboxStore;
import org.jline.reader.LineReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * Periodically checks {@link SoulInboxStore} and feeds new messages through {@link AgentLoop#processInput}.
 */
public final class SoulMailPollScheduler implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(SoulMailPollScheduler.class);
    private static final int MAX_BATCH = 15;
    private static final String DIM = "\u001B[90m";
    private static final String RESET = "\u001B[0m";

    private final ScheduledExecutorService executor;
    private final SoulInboxStore store;
    private final String soulId;
    private final AgentLoop agentLoop;
    private final PrintStream out;
    private final BooleanSupplier isolatedMode;
    private final Object agentInvokeLock;
    /** When non-null, notifications use {@link LineReader#printAbove(String)} to avoid corrupting the active prompt. */
    private final LineReader lineReader;

    private SoulMailPollScheduler(
            Path workDir,
            String soulId,
            AgentLoop agentLoop,
            PrintStream out,
            BooleanSupplier isolatedMode,
            Object agentInvokeLock,
            LineReader lineReader) {
        this.store = new SoulInboxStore(workDir);
        this.soulId = soulId;
        this.agentLoop = agentLoop;
        this.out = out;
        this.isolatedMode = isolatedMode;
        this.agentInvokeLock = agentInvokeLock;
        this.lineReader = lineReader;
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "zcode-soul-mail-poll");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * @return scheduler instance, or {@code null} when polling is disabled
     */
    public static SoulMailPollScheduler maybeStart(
            Path workDir,
            String soulId,
            int intervalSeconds,
            AgentLoop agentLoop,
            PrintStream out,
            BooleanSupplier isolatedMode,
            Object agentInvokeLock,
            LineReader lineReader) {
        if (intervalSeconds <= 0 || soulId == null || soulId.isBlank()) {
            return null;
        }
        SoulMailPollScheduler s = new SoulMailPollScheduler(
                workDir, soulId, agentLoop, out, isolatedMode, agentInvokeLock, lineReader);
        s.executor.scheduleWithFixedDelay(s::safeTick, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
        log.info("Soul mail auto-poll started: every {}s for soul '{}'", intervalSeconds, soulId);
        return s;
    }

    static String formatInboundAutoPrompt(List<SoulInboxStore.MailLine> lines, String selfSoulId) {
        StringBuilder sb = new StringBuilder();
        sb.append("[Automated soul_mail poll] You have ").append(lines.size());
        sb.append(" new message(s) in your inbox for soul `").append(selfSoulId).append("`.\n");
        sb.append("Handle them now: use tools as needed, complete actionable work, and reply via soul_mail ");
        sb.append("when a response to the sender is appropriate.\n\n");
        for (int i = 0; i < lines.size(); i++) {
            SoulInboxStore.MailLine m = lines.get(i);
            sb.append("--- #").append(i + 1).append(" from `").append(m.from).append("` ---\n");
            sb.append(m.body != null ? m.body : "").append("\n\n");
        }
        return sb.toString().trim();
    }

    private void safeTick() {
        try {
            tick();
        } catch (Throwable t) {
            log.error("Soul mail poll tick failed", t);
        }
    }

    private void tick() throws IOException {
        if (isolatedMode.getAsBoolean()) {
            return;
        }
        synchronized (agentInvokeLock) {
            List<SoulInboxStore.MailLine> pending = store.receive(soulId, MAX_BATCH, false);
            if (pending.isEmpty()) {
                return;
            }
            announcePollDispatch(pending.size());
            String prompt = formatInboundAutoPrompt(pending, soulId);
            try {
                agentLoop.processInput(prompt);
                store.receive(soulId, pending.size(), true);
            } catch (IOException e) {
                log.error("Soul mail auto-dispatch failed (inbox not consumed): {}", e.getMessage());
                throw e;
            } finally {
                refreshLineReaderDisplay();
            }
        }
    }

    private void announcePollDispatch(int count) {
        String line = DIM + "[soul_mail poll] " + count + " inbound message(s) — auto-dispatch" + RESET;
        if (lineReader != null) {
            lineReader.printAbove(line);
        } else {
            out.println();
            out.println(line);
            out.println();
            out.flush();
        }
    }

    private void refreshLineReaderDisplay() {
        if (lineReader == null) {
            return;
        }
        try {
            lineReader.callWidget(LineReader.REDISPLAY);
        } catch (Exception e) {
            log.info("LineReader REDISPLAY after poll: {}", e.getMessage());
        }
    }

    @Override
    public void close() {
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                log.info("Soul mail poll executor did not finish within 5s");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
