package com.zxx.zclaw.habit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Runtime habit simplification engine:
 * first full intent -> second shorthand suggest -> third shorthand auto.
 */
public final class HabitEngine {

    private static final Logger log = LoggerFactory.getLogger(HabitEngine.class);
    private static final long DEFAULT_COOLDOWN_MS = 24L * 60 * 60 * 1000;

    private final boolean enabled;
    private final boolean autoEnabled;
    private final int shortInputMaxChars;
    private final long cooldownMs;
    private final String soulId;
    private final HabitStore store;
    private HabitStore.State state;

    private String recentCanonicalIntent = "";
    private Resolution lastAutoResolution;

    public HabitEngine(
            Path workDir,
            String soulId,
            boolean enabled,
            boolean autoEnabled,
            int shortInputMaxChars) {
        this.enabled = enabled;
        this.autoEnabled = autoEnabled;
        this.shortInputMaxChars = Math.max(2, shortInputMaxChars);
        this.cooldownMs = DEFAULT_COOLDOWN_MS;
        this.soulId = (soulId == null || soulId.isBlank()) ? "default" : soulId;
        this.store = new HabitStore(workDir);
        this.state = safeLoad();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Resolution resolve(String input) {
        if (!enabled || input == null || input.isBlank()) {
            return Resolution.none(input);
        }
        String trimmed = input.trim();
        if (isUndo(trimmed)) {
            if (lastAutoResolution != null && lastAutoResolution.item != null) {
                applyReject(lastAutoResolution.item, System.currentTimeMillis());
                safeSave();
                Resolution r = Resolution.undo(trimmed, lastAutoResolution.expandedInput);
                lastAutoResolution = null;
                return r;
            }
            return Resolution.none(trimmed);
        }

        if (!isShortInput(trimmed)) {
            return Resolution.none(trimmed);
        }

        long now = System.currentTimeMillis();
        List<Candidate> candidates = findCandidates(trimmed);
        HabitStore.HabitItem mapped = candidates.isEmpty() ? null : candidates.get(0).item;
        if (mapped != null) {
            if (shouldAuto(mapped, now)) {
                Resolution r = Resolution.auto(trimmed, mapped.canonicalIntent, mapped);
                lastAutoResolution = r;
                return r;
            }
            return Resolution.suggest(trimmed, mapped.canonicalIntent, mapped, false);
        }

        if (!recentCanonicalIntent.isBlank() && seemsReferenceTo(trimmed, recentCanonicalIntent)) {
            return Resolution.suggest(trimmed, recentCanonicalIntent, null, true);
        }
        return Resolution.none(trimmed);
    }

    public void onSuggestionAccepted(Resolution resolution) {
        if (resolution == null || resolution.type != ResolutionType.SUGGEST) {
            return;
        }
        long now = System.currentTimeMillis();
        HabitStore.HabitItem item = resolution.item;
        if (item == null) {
            item = new HabitStore.HabitItem();
            item.shortcut = resolution.originalInput;
            item.canonicalIntent = resolution.expandedInput;
            item.state = HabitState.SUGGEST_ONLY;
            item.aliases = new ArrayList<>();
            state.items.add(item);
        } else if (item.aliases == null) {
            item.aliases = new ArrayList<>();
        }
        learnAlias(item, resolution.originalInput);
        item.confirmSuccessCount++;
        item.lastUsedAtEpochMs = now;
        item.cooldownUntilEpochMs = 0L;
        if (item.state == HabitState.NEW || item.state == HabitState.SUGGEST_ONLY || item.state == HabitState.DEGRADED) {
            item.state = HabitState.AUTO_READY;
        }
        safeSave();
    }

    public void onSuggestionRejected(Resolution resolution) {
        if (resolution == null || resolution.type != ResolutionType.SUGGEST || resolution.item == null) {
            return;
        }
        applyReject(resolution.item, System.currentTimeMillis());
        safeSave();
    }

    public void onAutoRoundCompleted(Resolution resolution, boolean success) {
        if (resolution == null || resolution.type != ResolutionType.AUTO || resolution.item == null) {
            return;
        }
        HabitStore.HabitItem item = resolution.item;
        if (success) {
            item.autoSuccessCount++;
            item.lastUsedAtEpochMs = System.currentTimeMillis();
            item.state = HabitState.AUTO_ACTIVE;
            item.cooldownUntilEpochMs = 0L;
            safeSave();
        }
    }

    public void rememberCanonicalIntent(String input) {
        if (!enabled || input == null) {
            return;
        }
        String trimmed = input.trim();
        if (trimmed.isBlank() || isShortInput(trimmed)) {
            return;
        }
        recentCanonicalIntent = trimmed;
    }

    private List<Candidate> findCandidates(String shortcut) {
        String key = normalizeShort(shortcut);
        List<Candidate> exact = new ArrayList<>();
        List<Candidate> fuzzy = new ArrayList<>();
        for (HabitStore.HabitItem item : state.items) {
            if (normalizeShort(item.shortcut).equals(key)) {
                exact.add(new Candidate(item, 1.0));
                continue;
            }
            if (item.aliases != null) {
                for (String alias : item.aliases) {
                    if (normalizeShort(alias).equals(key)) {
                        exact.add(new Candidate(item, 0.99));
                        break;
                    }
                }
            }
            double score = similarityScore(key, normalizeShort(item.shortcut));
            if (score >= 0.78) {
                fuzzy.add(new Candidate(item, score));
            }
        }
        if (!exact.isEmpty()) {
            exact.sort(Comparator.comparingDouble((Candidate c) -> c.score).reversed());
            return dedupByShortcut(exact);
        }
        fuzzy.sort(Comparator.comparingDouble((Candidate c) -> c.score).reversed());
        if (fuzzy.size() > 3) {
            fuzzy = new ArrayList<>(fuzzy.subList(0, 3));
        }
        return dedupByShortcut(fuzzy);
    }

    private static List<Candidate> dedupByShortcut(List<Candidate> in) {
        List<Candidate> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Candidate c : in) {
            String key = normalizeShort(c.item.shortcut);
            if (!seen.contains(key)) {
                seen.add(key);
                out.add(c);
            }
        }
        return out;
    }

    private boolean shouldAuto(HabitStore.HabitItem item, long now) {
        if (!autoEnabled) {
            return false;
        }
        if (item.cooldownUntilEpochMs > now) {
            return false;
        }
        return item.state == HabitState.AUTO_READY || item.state == HabitState.AUTO_ACTIVE;
    }

    private void applyReject(HabitStore.HabitItem item, long now) {
        item.rejectCount++;
        item.state = HabitState.DEGRADED;
        item.cooldownUntilEpochMs = now + cooldownMs;
        item.lastUsedAtEpochMs = now;
    }

    private HabitStore.State safeLoad() {
        try {
            return store.load(soulId);
        } catch (IOException e) {
            log.error("habit load failed for soul {}: {}", soulId, e.getMessage());
            return new HabitStore.State(1, new java.util.ArrayList<>());
        }
    }

    private void safeSave() {
        try {
            store.save(soulId, state);
        } catch (IOException e) {
            log.error("habit save failed for soul {}: {}", soulId, e.getMessage());
        }
    }

    private boolean isShortInput(String text) {
        if (text == null) {
            return false;
        }
        String t = text.trim();
        if (t.isBlank()) {
            return false;
        }
        return t.length() <= shortInputMaxChars && t.split("\\s+").length <= 4;
    }

    private static boolean isUndo(String text) {
        String t = text.toLowerCase(Locale.ROOT).trim();
        return "undo".equals(t) || "撤销".equals(t);
    }

    private static String normalizeShort(String text) {
        if (text == null) {
            return "";
        }
        return text.toLowerCase(Locale.ROOT).replaceAll("[\\p{Punct}\\s]+", "");
    }

    private static boolean seemsReferenceTo(String shortcut, String canonical) {
        String s = normalizeShort(shortcut);
        String c = normalizeShort(canonical);
        if (s.isBlank() || c.isBlank()) {
            return false;
        }
        return c.contains(s);
    }

    private static double similarityScore(String a, String b) {
        if (a.isBlank() || b.isBlank()) {
            return 0.0;
        }
        if (a.equals(b)) {
            return 1.0;
        }
        if (a.length() >= 2 && b.contains(a)) {
            return 0.9;
        }
        if (b.length() >= 2 && a.contains(b)) {
            return 0.88;
        }
        int lcs = longestCommonSubsequenceLength(a, b);
        int max = Math.max(a.length(), b.length());
        if (max == 0) {
            return 0.0;
        }
        return (double) lcs / max;
    }

    private static int longestCommonSubsequenceLength(String a, String b) {
        int n = a.length();
        int m = b.length();
        int[][] dp = new int[n + 1][m + 1];
        for (int i = 1; i <= n; i++) {
            char ca = a.charAt(i - 1);
            for (int j = 1; j <= m; j++) {
                if (ca == b.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
                }
            }
        }
        return dp[n][m];
    }

    private static void learnAlias(HabitStore.HabitItem item, String candidate) {
        if (item == null || candidate == null || candidate.isBlank()) {
            return;
        }
        if (item.aliases == null) {
            item.aliases = new ArrayList<>();
        }
        String key = normalizeShort(candidate);
        if (key.isBlank()) {
            return;
        }
        if (normalizeShort(item.shortcut).equals(key)) {
            return;
        }
        for (String alias : item.aliases) {
            if (normalizeShort(alias).equals(key)) {
                return;
            }
        }
        item.aliases.add(candidate.trim());
    }

    public List<String> listHabits() {
        List<HabitStore.HabitItem> items = new ArrayList<>(state.items);
        items.sort(Comparator.comparingLong((HabitStore.HabitItem i) -> i.lastUsedAtEpochMs).reversed());
        List<String> lines = new ArrayList<>();
        for (HabitStore.HabitItem item : items) {
            String aliases = (item.aliases == null || item.aliases.isEmpty())
                    ? "-"
                    : String.join(", ", item.aliases);
            lines.add(String.format(
                    Locale.ROOT,
                    "%s => %s | state=%s | confirm=%d auto=%d reject=%d | aliases=%s",
                    item.shortcut,
                    item.canonicalIntent,
                    item.state,
                    item.confirmSuccessCount,
                    item.autoSuccessCount,
                    item.rejectCount,
                    aliases));
        }
        return lines;
    }

    public boolean forgetShortcut(String shortcut) {
        if (shortcut == null || shortcut.isBlank()) {
            return false;
        }
        String key = normalizeShort(shortcut);
        boolean removed = state.items.removeIf(item -> {
            if (normalizeShort(item.shortcut).equals(key)) {
                return true;
            }
            if (item.aliases != null) {
                for (String alias : item.aliases) {
                    if (normalizeShort(alias).equals(key)) {
                        return true;
                    }
                }
            }
            return false;
        });
        if (removed) {
            safeSave();
        }
        return removed;
    }

    public enum ResolutionType {
        NONE,
        SUGGEST,
        AUTO,
        UNDO
    }

    public static final class Resolution {
        public final ResolutionType type;
        public final String originalInput;
        public final String expandedInput;
        public final HabitStore.HabitItem item;
        public final boolean fromRecentAnchor;
        private Resolution(
                ResolutionType type,
                String originalInput,
                String expandedInput,
                HabitStore.HabitItem item,
                boolean fromRecentAnchor) {
            this.type = type;
            this.originalInput = originalInput == null ? "" : originalInput;
            this.expandedInput = expandedInput == null ? "" : expandedInput;
            this.item = item;
            this.fromRecentAnchor = fromRecentAnchor;
        }

        public static Resolution none(String input) {
            return new Resolution(ResolutionType.NONE, input, "", null, false);
        }

        public static Resolution suggest(String original, String expanded, HabitStore.HabitItem item, boolean fromRecentAnchor) {
            return new Resolution(ResolutionType.SUGGEST, original, expanded, item, fromRecentAnchor);
        }

        public static Resolution auto(String original, String expanded, HabitStore.HabitItem item) {
            return new Resolution(ResolutionType.AUTO, original, expanded, item, false);
        }

        public static Resolution undo(String original, String expanded) {
            return new Resolution(ResolutionType.UNDO, original, expanded, null, false);
        }
    }

    private static final class Candidate {
        private final HabitStore.HabitItem item;
        private final double score;

        private Candidate(HabitStore.HabitItem item, double score) {
            this.item = item;
            this.score = score;
        }
    }
}
