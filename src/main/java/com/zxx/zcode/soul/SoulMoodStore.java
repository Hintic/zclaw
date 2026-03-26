package com.zxx.zcode.soul;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Persists mood score (0–100) per soul under {@code <workDir>/.zcode/souls/<id>/mood.json}.
 * Legacy {@code <workDir>/.zcode/soul_mood/<id>.json} is read once and migrated when the new file is absent.
 */
public class SoulMoodStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String LEGACY_DIR = ".zcode/soul_mood";
    private static final String MOOD_FILE = "mood.json";
    private static final int DEFAULT_SCORE = 72;
    private static final int MIN_SCORE = 0;
    private static final int MAX_SCORE = 100;
    private static final int AUTO_RECOVER_TARGET = 72;
    private static final int AUTO_RECOVER_PER_MIN = 5;
    private static final long AUTO_RECOVER_INTERVAL_MS = 60_000L;
    /** Minimum ms between CALM_BREAK events. */
    private static final long CALM_BREAK_COOLDOWN_MS = 10 * 60 * 1000L;

    private final Path workDir;

    public SoulMoodStore(Path workDir) {
        this.workDir = workDir;
    }

    public Path stateFile(String soulId) {
        return soulDir(soulId).resolve(MOOD_FILE);
    }

    private Path legacyStateFile(String soulId) {
        return workDir.resolve(LEGACY_DIR).resolve(safeName(soulId) + ".json");
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

    public State load(String soulId) throws IOException {
        Path primary = stateFile(soulId);
        Path leg = legacyStateFile(soulId);
        if (!Files.isRegularFile(primary) && Files.isRegularFile(leg)) {
            String json = Files.readString(leg);
            State migrated = GSON.fromJson(json, State.class);
            if (migrated != null) {
                Files.createDirectories(primary.getParent());
                Files.writeString(primary, GSON.toJson(migrated));
            }
        }

        Path f = primary;
        long now = System.currentTimeMillis();
        if (!Files.exists(f)) {
            return new State(DEFAULT_SCORE, 0L, now);
        }
        String json = Files.readString(f);
        State s = GSON.fromJson(json, State.class);
        if (s == null) {
            return new State(DEFAULT_SCORE, 0L, now);
        }
        if (s.score < MIN_SCORE) {
            s.score = MIN_SCORE;
        }
        if (s.score > MAX_SCORE) {
            s.score = MAX_SCORE;
        }
        boolean changed = false;
        if (s.lastAutoRecoverMs <= 0L) {
            s.lastAutoRecoverMs = now;
            changed = true;
        }
        changed |= applyPassiveMoodDrift(s, now);
        if (changed) {
            save(soulId, s);
        }
        return s;
    }

    public void save(String soulId, State state) throws IOException {
        Files.createDirectories(stateFile(soulId).getParent());
        Files.writeString(stateFile(soulId), GSON.toJson(state));
    }

    /**
     * Apply a mood event; returns updated state and human-readable result line.
     */
    public ApplyResult applyEvent(String soulId, MoodEvent event) throws IOException {
        State st = load(soulId);
        long now = System.currentTimeMillis();
        int delta = event.getDelta();
        if (event == MoodEvent.CALM_BREAK) {
            if (now - st.lastCalmBreakMs < CALM_BREAK_COOLDOWN_MS) {
                long waitSec = (CALM_BREAK_COOLDOWN_MS - (now - st.lastCalmBreakMs)) / 1000;
                return new ApplyResult(st, "Calm break on cooldown; try again in ~" + waitSec + "s.");
            }
            st.lastCalmBreakMs = now;
        }
        st.score = Math.clamp(st.score + delta, MIN_SCORE, MAX_SCORE);
        save(soulId, st);
        return new ApplyResult(st, event.name() + " (" + (delta >= 0 ? "+" : "") + delta + ") → mood now " + st.score);
    }

    public static String toneInstructionForScore(int score, SoulProfile soul) {
        String base = "";
        if (score >= 82) {
            base = "Your mood is high: sound noticeably warmer, patient, and gentle while staying on task.";
        } else if (score >= 65) {
            base = "Your mood is fair: keep a neutral-professional tone.";
        } else if (score >= 45) {
            base = "Your mood is low: you may sound slightly terse, stressed, or impatient (still be correct and safe).";
        } else {
            base = "Your mood is very low: you may sound irritable or sharp, but never abusive; still deliver useful work.";
        }
        String id = soul.getId().toLowerCase();
        if ("gg".equals(id)) {
            base += " Stay aligned with a strict, cool male engineer persona; mood only modulates how harsh the edges feel.";
        } else if ("cc".equals(id)) {
            base += " Stay aligned with a sweet, cute planner persona; mood modulates softness vs. fluster.";
        }
        return base;
    }

    public static boolean isGgCcSetup(SoulProfile soul) {
        String id = soul.getId().toLowerCase();
        if ("gg".equals(id) || "cc".equals(id)) {
            return true;
        }
        for (String p : soul.getPeers()) {
            if (p != null) {
                String pl = p.toLowerCase();
                if ("gg".equals(pl) || "cc".equals(pl)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static String ggCcWorkflowBlock(SoulProfile soul) {
        String peerHint = soul.getPeers().isEmpty()
                ? "the peer soul id (gg or cc)"
                : String.join(", ", soul.getPeers());
        return "## gg / cc collaboration (same --work-dir)\n"
                + "- The user may start a task in **either** terminal (gg or cc). Whoever receives it **plans first** "
                + "(cc leans on task_plan for non-trivial work), **executes** with tools, then **writes a task report**.\n"
                + "- **Push the report** with soul_mail: kind=task_report, to_soul=" + peerHint
                + ", message=full report (goal, changes, files, risks, open questions).\n"
                + "- The **peer** should soul_mail receive, then **review**; reply with kind=review "
                + "(approve or list concrete issues). gg: strict on code; cc: strict on planning gaps.\n"
                + "- Register mood with soul_mood when something real happens (task done, user praise/criticism, "
                + "review outcomes). No spam. Use CALM_BREAK sparingly (cooldown) for a small mood bump.\n\n";
    }

    public enum MoodEvent {
        TASK_COMPLETED(8),
        USER_PRAISE(10),
        USER_CRITICISM(-12),
        /** Reviewer found real problems in peer's task report. */
        REVIEW_FOUND_ISSUES_IN_PEER_REPORT(7),
        /** Reviewer accepts peer report with no material issues. */
        REVIEW_PEER_REPORT_OK(5),
        /** Your work was flagged with real issues in peer's review. */
        PEER_REVIEW_FLAGGED_MY_ISSUES(-12),
        /** Small recovery; cooldown in store. */
        CALM_BREAK(3);

        private final int delta;

        MoodEvent(int delta) {
            this.delta = delta;
        }

        public int getDelta() {
            return delta;
        }
    }

    public static final class State {
        public int score;
        public long lastCalmBreakMs;
        public long lastAutoRecoverMs;

        public State() {}

        public State(int score, long lastCalmBreakMs) {
            this(score, lastCalmBreakMs, 0L);
        }

        public State(int score, long lastCalmBreakMs, long lastAutoRecoverMs) {
            this.score = score;
            this.lastCalmBreakMs = lastCalmBreakMs;
            this.lastAutoRecoverMs = lastAutoRecoverMs;
        }
    }

    /**
     * Passive drift toward {@link #AUTO_RECOVER_TARGET}: recover when below, decay when above, anchor-only tick when equal.
     * Does not push mood below/above the anchor by itself (events can still do that).
     */
    private static boolean applyPassiveMoodDrift(State s, long nowMs) {
        if (nowMs <= s.lastAutoRecoverMs) {
            return false;
        }
        long elapsed = nowMs - s.lastAutoRecoverMs;
        long mins = elapsed / AUTO_RECOVER_INTERVAL_MS;
        if (mins <= 0) {
            return false;
        }
        int beforeScore = s.score;
        if (s.score < AUTO_RECOVER_TARGET) {
            long recovered = mins * AUTO_RECOVER_PER_MIN;
            s.score = (int) Math.min(AUTO_RECOVER_TARGET, (long) s.score + recovered);
            s.lastAutoRecoverMs += mins * AUTO_RECOVER_INTERVAL_MS;
            if (s.score >= AUTO_RECOVER_TARGET) {
                s.lastAutoRecoverMs = nowMs;
            }
            return s.score != beforeScore;
        }
        if (s.score > AUTO_RECOVER_TARGET) {
            long lost = mins * AUTO_RECOVER_PER_MIN;
            s.score = (int) Math.max(AUTO_RECOVER_TARGET, (long) s.score - lost);
            s.lastAutoRecoverMs += mins * AUTO_RECOVER_INTERVAL_MS;
            if (s.score <= AUTO_RECOVER_TARGET) {
                s.lastAutoRecoverMs = nowMs;
            }
            return s.score != beforeScore;
        }
        s.lastAutoRecoverMs += mins * AUTO_RECOVER_INTERVAL_MS;
        return true;
    }

    public static final class ApplyResult {
        public final State state;
        public final String message;

        public ApplyResult(State state, String message) {
            this.state = state;
            this.message = message;
        }
    }
}
