package com.zxx.zclaw.tool;

import com.zxx.zclaw.soul.SoulMoodStore;
import com.zxx.zclaw.soul.SoulMoodStore.MoodEvent;
import com.zxx.zclaw.soul.SoulProfile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Mood simulation: score 0–100 affects tone hints in the system prompt. Events adjust the score on disk.
 */
public class SoulMoodTool implements Tool {

    private final SoulMoodStore store;
    private final SoulProfile profile;
    private final String soulId;
    private final boolean enabled;

    public SoulMoodTool(Path workDir, SoulProfile soul) {
        this.store = new SoulMoodStore(workDir);
        this.profile = soul != null ? soul : SoulProfile.defaultProfile();
        this.soulId = profile.getId();
        this.enabled = soul != null && !soul.isDefault();
    }

    @Override
    public String name() {
        return "soul_mood";
    }

    @Override
    public String description() {
        return """
                Track simulated mood (0–100) for tone. action=status reads current score and tone hint.
                action=event applies one discrete event (adjusts score, clamped). Call honestly when the
                conversation implies a real outcome: task finished, user praised or criticized you,
                you reviewed a peer's task report and found issues or approved it, or a peer's review
                flagged problems in your work. CALM_BREAK gives a small boost with a cooldown.
                """.trim();
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("action", Map.of(
                "type", "string",
                "enum", List.of("status", "event"),
                "description", "status: read mood; event: apply one MoodEvent"));
        props.put("event", Map.of(
                "type", "string",
                "enum", List.of(
                        "TASK_COMPLETED",
                        "USER_PRAISE",
                        "USER_CRITICISM",
                        "REVIEW_FOUND_ISSUES_IN_PEER_REPORT",
                        "REVIEW_PEER_REPORT_OK",
                        "PEER_REVIEW_FLAGGED_MY_ISSUES",
                        "CALM_BREAK"),
                "description", "Required when action=event"));

        return Map.of(
                "type", "object",
                "properties", props,
                "required", List.of("action"));
    }

    @Override
    public String execute(Map<String, Object> args) {
        if (!enabled) {
            return "soul_mood is only active with a non-default --soul (e.g. gg or cc).";
        }
        String action = stringArg(args, "action");
        if (action == null || action.isBlank()) {
            return "Error: action required (status or event).";
        }
        try {
            return switch (action.trim().toLowerCase(Locale.ROOT)) {
                case "status" -> handleStatus();
                case "event" -> handleEvent(args);
                default -> "Error: use status or event.";
            };
        } catch (IOException e) {
            return "Error: soul_mood I/O: " + e.getMessage();
        }
    }

    private String handleStatus() throws IOException {
        SoulMoodStore.State st = store.load(soulId);
        String tone = SoulMoodStore.toneInstructionForScore(st.score, profile);
        return "Mood score: " + st.score + "/100\nTone hint: " + tone;
    }

    private String handleEvent(Map<String, Object> args) throws IOException {
        String ev = stringArg(args, "event");
        if (ev == null || ev.isBlank()) {
            return "Error: event is required when action=event.";
        }
        MoodEvent me;
        try {
            me = MoodEvent.valueOf(ev.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return "Error: unknown event name.";
        }
        SoulMoodStore.ApplyResult ar = store.applyEvent(soulId, me);
        String tone = SoulMoodStore.toneInstructionForScore(ar.state.score, profile);
        return ar.message + "\nMood score now: " + ar.state.score + "/100\nTone hint: " + tone;
    }

    private static String stringArg(Map<String, Object> args, String key) {
        Object v = args.get(key);
        return v == null ? null : v.toString();
    }
}
