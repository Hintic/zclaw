package com.zxx.zcode.cli;

/**
 * Removes model "reasoning" spans from streamed text. When an opening marker is seen,
 * nothing is emitted until the matching closing marker arrives (safe across chunk splits).
 */
public final class StreamThinkingStripper {

    private record Pair(String open, String close) {}

    private static final Pair[] PAIRS = {
            new Pair("`think`", "`/think`"),
            new Pair("<think>", "</think>"),
            new Pair("`思考`", "`/思考`"),
    };

    private final StringBuilder buf = new StringBuilder();
    private boolean inThinking;

    private static int maxOpenLength() {
        int m = 0;
        for (Pair p : PAIRS) {
            m = Math.max(m, p.open.length());
        }
        return m;
    }

    private static String stripCompleteBlocks(String s) {
        String cur = s;
        while (true) {
            int bestStart = -1;
            int bestEnd = -1;
            for (Pair p : PAIRS) {
                int o = cur.indexOf(p.open);
                if (o < 0) {
                    continue;
                }
                int c = cur.indexOf(p.close, o + p.open.length());
                if (c < 0) {
                    continue;
                }
                int end = c + p.close.length();
                if (bestStart < 0 || o < bestStart) {
                    bestStart = o;
                    bestEnd = end;
                }
            }
            if (bestStart < 0) {
                return cur;
            }
            cur = cur.substring(0, bestStart) + cur.substring(bestEnd);
        }
    }

    private static int unsafeSuffixLength(String s) {
        int limit = Math.min(s.length(), maxOpenLength() - 1);
        int best = 0;
        for (Pair p : PAIRS) {
            String open = p.open;
            for (int len = 1; len <= limit; len++) {
                String suf = s.substring(s.length() - len);
                if (open.startsWith(suf)) {
                    best = Math.max(best, len);
                }
            }
        }
        return best;
    }

    /**
     * @return prefix of filtered text safe to print now
     */
    public String feed(String delta) {
        if (delta == null || delta.isEmpty()) {
            return "";
        }
        buf.append(delta);
        StringBuilder emitted = new StringBuilder();

        while (true) {
            if (inThinking) {
                // Models may mix markers (e.g. `think` open with `</think>` close). Match any known close.
                int bestClose = -1;
                int bestCloseLen = 0;
                for (Pair p : PAIRS) {
                    int c = buf.indexOf(p.close);
                    if (c >= 0 && (bestClose < 0 || c < bestClose)) {
                        bestClose = c;
                        bestCloseLen = p.close.length();
                    }
                }
                if (bestClose >= 0) {
                    buf.delete(0, bestClose + bestCloseLen);
                    inThinking = false;
                    continue;
                }
                break;
            }

            String s = stripCompleteBlocks(buf.toString());
            buf.setLength(0);
            buf.append(s);
            s = buf.toString();

            int earliest = -1;
            Pair openPair = null;
            for (Pair p : PAIRS) {
                int i = s.indexOf(p.open);
                if (i >= 0 && (earliest < 0 || i < earliest)) {
                    earliest = i;
                    openPair = p;
                }
            }
            if (earliest >= 0 && openPair != null) {
                emitted.append(s, 0, earliest);
                s = s.substring(earliest + openPair.open.length());
                buf.setLength(0);
                buf.append(s);
                inThinking = true;
                continue;
            }

            int hold = unsafeSuffixLength(s);
            if (s.length() > hold) {
                emitted.append(s, 0, s.length() - hold);
                buf.setLength(0);
                buf.append(s.substring(s.length() - hold));
            }
            break;
        }

        return emitted.toString();
    }

    /**
     * Remaining buffer after stream end. Never drops buffered text: if a close tag never arrived,
     * the tail (often the real answer) is still returned so the UI does not go blank.
     */
    public String drain() {
        String raw = buf.toString();
        buf.setLength(0);
        inThinking = false;
        return stripCompleteBlocks(raw);
    }
}
