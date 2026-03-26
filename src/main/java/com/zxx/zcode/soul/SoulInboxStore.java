package com.zxx.zcode.soul;

import com.google.gson.Gson;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Append-only JSONL inbox per soul under {@code <workDir>/.zcode/souls/<id>/inbox.jsonl}.
 * Legacy {@code <workDir>/.zcode/soul_inbox/<id>.jsonl} is read when the new inbox is missing or empty.
 * Sends always append to the new path. File locks allow two JVM processes to share the same work directory safely.
 */
public class SoulInboxStore {

    private static final Gson GSON = new Gson();
    private static final String LEGACY_DIR = ".zcode/soul_inbox";
    private static final String INBOX_FILE = "inbox.jsonl";
    private static final long MAX_INBOX_BYTES = 2_000_000;

    private final Path workDir;

    public SoulInboxStore(Path workDir) {
        this.workDir = workDir;
    }

    /** Primary inbox path (used for {@link #send}). */
    public Path inboxFile(String soulId) {
        return soulDir(soulId).resolve(INBOX_FILE);
    }

    private Path legacyInboxFile(String soulId) {
        return workDir.resolve(LEGACY_DIR).resolve(safeName(soulId) + ".jsonl");
    }

    private Path soulDir(String soulId) {
        return workDir.resolve(".zcode/souls").resolve(safeName(soulId));
    }

    private static String safeName(String soulId) {
        if (soulId == null || soulId.isBlank()) {
            return "default";
        }
        return soulId.replaceAll("[^a-zA-Z0-9_.-]", "_");
    }

    /**
     * Inbox to read from: prefer non-empty primary; else non-empty legacy.
     */
    private Path inboxReadTarget(String soulId) throws IOException {
        Path primary = inboxFile(soulId);
        if (Files.isRegularFile(primary) && Files.size(primary) > 0) {
            return primary;
        }
        Path leg = legacyInboxFile(soulId);
        if (Files.isRegularFile(leg) && Files.size(leg) > 0) {
            return leg;
        }
        return primary;
    }

    public void send(String fromSoulId, String toSoulId, String body) throws IOException {
        if (toSoulId == null || toSoulId.isBlank()) {
            throw new IOException("to_soul is required");
        }
        if (body == null || body.isBlank()) {
            throw new IOException("message body is required");
        }
        Files.createDirectories(inboxFile(toSoulId).getParent());
        MailLine line = new MailLine(
                safeName(fromSoulId),
                safeName(toSoulId),
                body.trim().replace('\n', ' ').replace('\r', ' '),
                System.currentTimeMillis());
        byte[] bytes = (GSON.toJson(line) + "\n").getBytes(StandardCharsets.UTF_8);

        Path target = inboxFile(toSoulId);
        try (FileChannel ch = FileChannel.open(target,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.APPEND)) {
            try (FileLock ignored = ch.lock(0L, Long.MAX_VALUE, false)) {
                ch.write(ByteBuffer.wrap(bytes));
            }
        }
    }

    /**
     * Read up to {@code limit} messages from the inbox. If {@code consume}, removes them after read.
     */
    public List<MailLine> receive(String forSoulId, int limit, boolean consume) throws IOException {
        if (limit < 1) {
            limit = 20;
        }
        Path target = inboxReadTarget(forSoulId);
        if (!Files.exists(target)) {
            return List.of();
        }

        try (FileChannel ch = FileChannel.open(target, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            try (FileLock ignored = ch.lock(0L, Long.MAX_VALUE, false)) {
                long sz = ch.size();
                if (sz == 0) {
                    return List.of();
                }
                if (sz > MAX_INBOX_BYTES) {
                    throw new IOException("Inbox too large; trim or delete " + target);
                }
                ByteBuffer buf = ByteBuffer.allocate((int) sz);
                ch.position(0);
                ch.read(buf);
                String text = new String(buf.array(), 0, buf.position(), StandardCharsets.UTF_8);
                List<String> lines = new ArrayList<>(text.lines().filter(s -> !s.isBlank()).toList());
                if (lines.isEmpty()) {
                    return List.of();
                }
                int take = Math.min(limit, lines.size());
                List<MailLine> parsed = new ArrayList<>(take);
                for (int i = 0; i < take; i++) {
                    try {
                        parsed.add(GSON.fromJson(lines.get(i), MailLine.class));
                    } catch (Exception e) {
                        parsed.add(new MailLine("?", "?", lines.get(i), 0L));
                    }
                }
                if (consume && take > 0) {
                    List<String> rest = lines.subList(take, lines.size());
                    ch.truncate(0);
                    if (!rest.isEmpty()) {
                        byte[] out = (String.join("\n", rest) + "\n").getBytes(StandardCharsets.UTF_8);
                        ch.position(0);
                        ch.write(ByteBuffer.wrap(out));
                        ch.truncate(out.length);
                    }
                }
                return parsed;
            }
        }
    }

    public static final class MailLine {
        public String from;
        public String to;
        public String body;
        public long ts;

        public MailLine() {}

        public MailLine(String from, String to, String body, long ts) {
            this.from = from;
            this.to = to;
            this.body = body;
            this.ts = ts;
        }
    }
}
