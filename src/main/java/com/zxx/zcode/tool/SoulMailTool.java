package com.zxx.zcode.tool;

import com.zxx.zcode.soul.SoulInboxStore;
import com.zxx.zcode.soul.SoulProfile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Peer messaging for multi-instance collaboration. Uses {@link SoulInboxStore} ({@code .zcode/souls/<id>/inbox.jsonl}, legacy {@code soul_inbox/}).
 */
public class SoulMailTool implements Tool {

    private final SoulInboxStore store;
    private final String selfSoulId;

    public SoulMailTool(Path workDir, SoulProfile soul) {
        this.store = new SoulInboxStore(workDir);
        this.selfSoulId = soul != null ? soul.getId() : "default";
    }

    @Override
    public String name() {
        return "soul_mail";
    }

    @Override
    public String description() {
        return """
                Exchange short messages with another z-code instance running in the SAME work directory
                with a different --soul id. Use this to coordinate (e.g. architect vs implementer).
                action=send delivers to the peer's inbox; action=receive lists messages to you.
                Optional kind: normal (default), task_report (handoff summary for peer review), review (peer feedback),
                skill_suggest (recommend a task that fits the peer's published skill — see system prompt Peer skills).
                After reading, call receive again with consume=true to remove handled messages.
                """.trim();
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("action", Map.of(
                "type", "string",
                "enum", List.of("send", "receive"),
                "description", "send: post to peer inbox; receive: read your inbox"));
        props.put("to_soul", Map.of(
                "type", "string",
                "description", "Target soul id (required for send); must match the other terminal's --soul"));
        props.put("message", Map.of(
                "type", "string",
                "description", "Plain text (required for send); task_report/review can be longer"));
        props.put("kind", Map.of(
                "type", "string",
                "enum", List.of("normal", "task_report", "review", "skill_suggest"),
                "description", "Optional; prefixes message (task report, review, or skill handoff suggestion)"));
        props.put("limit", Map.of(
                "type", "integer",
                "description", "Max messages for receive (default 15)"));
        props.put("consume", Map.of(
                "type", "boolean",
                "description", "If true with receive, remove listed messages from inbox after returning"));

        return Map.of(
                "type", "object",
                "properties", props,
                "required", List.of("action"));
    }

    @Override
    public String execute(Map<String, Object> args) {
        String action = stringArg(args, "action");
        if (action == null || action.isBlank()) {
            return "Error: action is required (send or receive).";
        }
        try {
            return switch (action.trim().toLowerCase()) {
                case "send" -> handleSend(args);
                case "receive" -> handleReceive(args);
                default -> "Error: unknown action. Use send or receive.";
            };
        } catch (IOException e) {
            return "Error: soul_mail I/O failed: " + e.getMessage();
        }
    }

    private String handleSend(Map<String, Object> args) throws IOException {
        String to = stringArg(args, "to_soul");
        String msg = stringArg(args, "message");
        if (to == null || to.isBlank()) {
            return "Error: to_soul is required for send.";
        }
        if (msg == null || msg.isBlank()) {
            return "Error: message is required for send.";
        }
        if (to.trim().equalsIgnoreCase(selfSoulId)) {
            return "Error: cannot send to yourself; use another soul's id.";
        }
        String kind = stringArg(args, "kind");
        String body = prefixForKind(kind) + msg;
        store.send(selfSoulId, to.trim(), body);
        return "Sent to soul '" + to.trim() + "' from '" + selfSoulId + "' (" + kindLabel(kind) + ").";
    }

    private String handleReceive(Map<String, Object> args) throws IOException {
        int limit = intArg(args, "limit", 15);
        boolean consume = booleanArg(args, "consume");
        List<SoulInboxStore.MailLine> lines = store.receive(selfSoulId, limit, consume);
        if (lines.isEmpty()) {
            return "(no messages for soul '" + selfSoulId + "')";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(lines.size()).append(" message(s) for '").append(selfSoulId).append("':\n");
        for (int i = 0; i < lines.size(); i++) {
            SoulInboxStore.MailLine m = lines.get(i);
            sb.append("\n--- #").append(i + 1).append(" from ").append(m.from).append(" ---\n");
            sb.append(m.body != null ? m.body : "").append("\n");
            if (m.ts > 0) {
                sb.append("(at ").append(m.ts).append(")\n");
            }
        }
        if (consume) {
            sb.append("\n(consumed from inbox)\n");
        }
        return sb.toString().trim();
    }

    private static String prefixForKind(String kind) {
        if (kind == null || kind.isBlank()) {
            return "";
        }
        return switch (kind.trim().toLowerCase()) {
            case "task_report" -> "[TASK_REPORT]\n";
            case "review" -> "[REVIEW]\n";
            case "skill_suggest" -> "[SKILL_SUGGEST]\n";
            default -> "";
        };
    }

    private static String kindLabel(String kind) {
        if (kind == null || kind.isBlank()) {
            return "normal";
        }
        return kind.trim().toLowerCase();
    }

    private static String stringArg(Map<String, Object> args, String key) {
        Object v = args.get(key);
        return v == null ? null : v.toString();
    }

    private static int intArg(Map<String, Object> args, String key, int defaultVal) {
        Object v = args.get(key);
        if (v instanceof Number n) {
            return n.intValue();
        }
        if (v instanceof String s && !s.isBlank()) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
                return defaultVal;
            }
        }
        return defaultVal;
    }

    private static boolean booleanArg(Map<String, Object> args, String key) {
        Object v = args.get(key);
        if (v instanceof Boolean b) {
            return b;
        }
        if (v instanceof String s) {
            return Boolean.parseBoolean(s.trim());
        }
        return false;
    }
}
