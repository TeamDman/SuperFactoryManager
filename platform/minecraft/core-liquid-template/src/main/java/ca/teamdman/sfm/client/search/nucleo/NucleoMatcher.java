/* SPDX-License-Identifier: MPL-2.0
 * Derived from helix-editor/nucleo, Copyright Pascal Kuthe and contributors.
 * Upstream: 8c16d47cdfa9607d3e44df5f81c635c6f43c65ee (nucleo-matcher 0.3.1).
 * See scripts/nucleo-parity/README.md and LICENSE-MPL-2.0.txt.
 */
package ca.teamdman.sfm.client.search.nucleo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Literal fuzzy matcher in Nucleo's codepoint feature profile. Reuse per worker; not thread safe. */
public final class NucleoMatcher {
    public static final String UPSTREAM_REVISION = "8c16d47cdfa9607d3e44df5f81c635c6f43c65ee";
    public static final int MAX_QUERY_CODEPOINTS = 2048;
    public static final int MAX_CANDIDATE_CODEPOINTS = 65535;
    public enum CaseMode { RESPECT, IGNORE, SMART }
    public enum Normalization { NEVER, SMART }
    /** PATHS matches upstream set_match_paths() on Unix (both '/' and ':' delimit). */
    public enum Boundaries { DEFAULT, PATHS }
    public record Options(CaseMode caseMode, Normalization normalization, Boundaries boundaries,
                          boolean preferPrefix) {
        public static final Options DEFAULT = new Options(CaseMode.SMART, Normalization.SMART,
                Boundaries.DEFAULT, false);
        public Options {
            Objects.requireNonNull(caseMode);
            Objects.requireNonNull(normalization);
            Objects.requireNonNull(boundaries);
        }
    }
    /** Half-open UTF-16 range into the original candidate, safe for String.substring(). */
    public record Span(int start, int end) {}
    /** Scores are unsigned 16-bit Nucleo scores: higher is better. Positions are immutable. */
    public record Match(int score, List<Integer> codePointPositions, List<Span> utf16Spans) {
        public Match {
            codePointPositions = List.copyOf(codePointPositions);
            utf16Spans = List.copyOf(utf16Spans);
        }
    }
    /** Immutable text and codepoint/UTF-16 mapping, reusable across queries and workers. */
    public static final class Candidate {
        private final String text;
        private final int[] points;
        private final int[] offsets;
        private final boolean ascii;
        public Candidate(String text) {
            this.text = Objects.requireNonNull(text);
            this.points = scalarValues(text, MAX_CANDIDATE_CODEPOINTS);
            this.offsets = new int[points.length + 1];
            boolean allAscii = true;
            for (int i = 0; i < points.length; i++) {
                offsets[i + 1] = offsets[i] + Character.charCount(points[i]);
                allAscii &= points[i] < 128;
            }
            this.ascii = allAscii && !text.contains("\r\n");
        }
        public String text() { return text; }
    }

    private final int[] needle;
    private final boolean needleAscii;
    private final boolean ignoreCase;
    private final boolean normalize;
    private final Options options;
    private int[] chars = new int[0], bonuses = new int[0];
    private int[] scores = new int[0], nextScores = new int[0];
    private int[] consecutive = new int[0], nextConsecutive = new int[0];
    private byte[] trace = new byte[0];
    private final int[] positions;
    private final int[] rowStarts;
    private int positionCount;

    public NucleoMatcher(String query) { this(query, Options.DEFAULT); }
    public NucleoMatcher(String query, Options options) {
        this.options = Objects.requireNonNull(options);
        needle = scalarValues(Objects.requireNonNull(query), MAX_QUERY_CODEPOINTS);
        boolean ascii = true, fold = options.caseMode != CaseMode.RESPECT;
        boolean norm = options.normalization == Normalization.SMART;
        for (int i = 0; i < needle.length; i++) {
            int c = needle[i];
            ascii &= c < 128;
            if (options.caseMode == CaseMode.IGNORE) needle[i] = NucleoUnicode.fold(c);
            if (options.caseMode == CaseMode.SMART && NucleoUnicode.fold(c) != c) fold = false;
            norm &= NucleoUnicode.normalize(needle[i]) == needle[i];
        }
        needleAscii = ascii; // Preserve original representation, even if case folding produces ASCII.
        ignoreCase = fold;
        normalize = norm;
        positions = new int[needle.length];
        rowStarts = new int[needle.length];
    }

    public Match match(String candidate) { return match(new Candidate(candidate)); }
    /** Returns null when the candidate is not a subsequence match. */
    public Match match(Candidate candidate) {
        int score = run(Objects.requireNonNull(candidate), true);
        if (score < 0) return null;
        var points = new ArrayList<Integer>(needle.length);
        var spans = new ArrayList<Span>();
        for (int p = 0; p < positionCount; p++) {
            int i = positions[p];
            points.add(i);
            int start = candidate.offsets[i], end = candidate.offsets[i + 1];
            if (!spans.isEmpty() && spans.get(spans.size() - 1).end == start) {
                Span previous = spans.remove(spans.size() - 1);
                spans.add(new Span(previous.start, end));
            } else spans.add(new Span(start, end));
        }
        return new Match(score, points, spans);
    }
    /** Score-only pass for ranking: -1 means absent. Compute highlights only for retained results. */
    public int score(Candidate candidate) { return run(Objects.requireNonNull(candidate), false); }

    private int run(Candidate h, boolean indices) {
        if (indices) positionCount = 0;
        int n = h.points.length, m = needle.length;
        if (m > n) return -1;
        if (m == 0) return 0;
        if (h.ascii && !needleAscii) return -1;
        if (m == n) return exact(h, 0, n, indices);
        if (m == 1) return single(h, indices);
        int start = 0;
        while (start <= n - m && normalized(h.points[start]) != needle[0]) start++;
        if (start > n - m) return -1;
        int greedyEnd = start + 1;
        if (h.ascii && needleAscii) {
            for (int j = 1; j < m; j++) {
                while (greedyEnd < n && normalized(h.points[greedyEnd]) != needle[j]) greedyEnd++;
                if (greedyEnd == n) return -1;
                greedyEnd++;
            }
        }
        int end = n;
        while (end > start + 1 && normalized(h.points[end - 1]) != needle[m - 1]) end--;
        if (end - start < m) return -1;
        if (end - start == m) return exact(h, start, end, indices);
        int len = end - start;
        // Upstream's slab has 133120 bytes, with an 8-byte ScoreCell. Match its fallback threshold.
        long layout = (long) len * (h.ascii ? 1 : 4) + len;
        layout = (layout + 1) / 2 * 2 + 2L * m;
        layout = (layout + 7) / 8 * 8 + 8L * (len + 1 - m) + (long) (len + 1 - m) * m;
        if ((long) len * m > 102400 || layout > 133120) {
            return greedy(h, start, greedyEnd, indices);
        }
        return optimal(h, start, end, indices);
    }

    private int exact(Candidate h, int start, int end, boolean indices) {
        for (int i = start; i < end; i++)
            if (normalized(h.points[i]) != normalized(needle[i - start])) return -1;
        return calculate(h, start, end, indices);
    }

    private int single(Candidate h, boolean indices) {
        int best = 0, pos = 0;
        int previous = initialClass();
        boolean started = false;
        for (int i = 0; i < h.points.length; i++) {
            int c = h.points[i], cls = charClass(c);
            if (!h.ascii && !started) {
                if (normalized(c) != needle[0]) continue;
                previous = i == 0 ? initialClass() : charClass(h.points[i - 1]);
                started = true;
                pos = i;
            }
            if (classNormalized(c, cls) != needle[0]) continue;
            int prev = h.ascii ? (i == 0 ? initialClass() : charClass(h.points[i - 1])) : previous;
            int bonus = bonus(prev, cls);
            previous = cls; // Preserve upstream Unicode single-character previous-match behavior.
            int score = 16 + 2 * bonus;
            if (score > best) {
                best = score; pos = i;
                if (bonus >= whiteBonus()) break;
            }
        }
        if (best == 0 && (h.ascii || !started)) return -1;
        if (indices) { positions[0] = pos; positionCount = 1; }
        return best; // Upstream's single-character shortcut ignores prefer_prefix.
    }

    private int greedy(Candidate h, int start, int end, boolean indices) {
        if (!h.ascii || !needleAscii) {
            for (int j = 1; j < needle.length; j++) {
                while (end < h.points.length && normalized(h.points[end]) != needle[j]) end++;
                if (end == h.points.length) return -1;
                end++;
            }
        }
        int j = needle.length - 1;
        for (int i = end - 1; i >= start; i--) {
            if (normalized(h.points[i]) == needle[j] && --j < 0) { start = i; break; }
        }
        return calculate(h, start, end, indices);
    }

    private int calculate(Candidate h, int start, int end, boolean indices) {
        int previous = start == 0 ? initialClass() : charClass(h.points[start - 1]);
        int cls = charClass(h.points[start]);
        int firstBonus = bonus(previous, cls), score = 16 + firstBonus * 2;
        if (indices) positions[0] = start;
        int j = 1, run = 1;
        boolean gap = false;
        previous = cls;
        for (int i = start + 1; i < end; i++) {
            cls = charClass(h.points[i]);
            if (classNormalized(h.points[i], cls) == needle[Math.min(j, needle.length - 1)]) {
                if (indices) positions[j] = i;
                j++;
                int bonus = bonus(previous, cls);
                if (run != 0) {
                    if (bonus >= 8 && bonus > firstBonus) firstBonus = bonus;
                    bonus = Math.max(Math.max(bonus, firstBonus), 4);
                } else firstBonus = bonus;
                score += 16 + bonus;
                gap = false; run++;
            } else {
                score = Math.max(0, score - (gap ? 1 : 3));
                gap = true; run = 0;
            }
            previous = cls;
        }
        if (options.preferPrefix) {
            // Match release-mode u16 arithmetic in upstream calculate_score.
            int penalty = start == 0 ? 0 : (3 + 3 * (start - 1)) & 0xffff;
            score += Math.max(0, 8 - penalty / 2);
        }
        if (indices) positionCount = j;
        return score;
    }

    private int optimal(Candidate h, int start, int end, boolean indices) {
        int n = end - start, m = needle.length;
        ensureCapacity(n, indices ? n * m : 0);
        int previous = start == 0 ? initialClass() : charClass(h.points[start - 1]), j = 0;
        for (int i = 0; i < n; i++) {
            int c = h.points[start + i], cls = charClass(c);
            chars[i] = classNormalized(c, cls);
            bonuses[i] = bonus(previous, cls);
            previous = cls;
            if (j < m && chars[i] == needle[j]) rowStarts[j++] = i;
        }
        if (j < m) return -1;
        Arrays.fill(scores, 0, n, 0);
        Arrays.fill(consecutive, 0, n, 0);
        if (indices) Arrays.fill(trace, 0, n * m, (byte) 0);
        int prefix = !options.preferPrefix ? 0 : start == 0 ? 16 : Math.max(0, 13 - (start - 1));
        for (int i = 0; i <= n - m; i++) {
            if (chars[i] == needle[0]) {
                scores[i] = 16 + bonuses[i] * 2 + Math.max(0, prefix - i) / 2;
                consecutive[i] = bonuses[i];
            }
        }
        for (int row = 0; row < m - 1; row++) {
            Arrays.fill(nextScores, 0, n, 0);
            Arrays.fill(nextConsecutive, 0, n, 0);
            int p = 0, prevM = 0;
            for (int col = rowStarts[row]; col <= n - m + row; col++) {
                int fromM = Math.max(0, prevM - 3), fromP = Math.max(0, p - 1);
                boolean pMatch = fromM > fromP;
                p = pMatch ? fromM : fromP;
                if (indices && pMatch) trace[row * n + col] |= 1;
                if (col + 1 >= rowStarts[row + 1] && chars[col + 1] == needle[row + 1]) {
                    int bonus = bonuses[col + 1];
                    int cb = Math.max(consecutive[col], 4);
                    if (bonus >= 8 && bonus > cb) cb = bonus;
                    int match = scores[col] + Math.max(cb, bonus), skip = p + bonus;
                    boolean useM = scores[col] != 0 && match > skip;
                    nextScores[col + 1] = 16 + (useM ? match : skip);
                    nextConsecutive[col + 1] = useM ? cb : bonus;
                    if (indices && useM) trace[(row + 1) * n + col + 1] |= 2;
                }
                prevM = scores[col];
            }
            int[] swap = scores; scores = nextScores; nextScores = swap;
            swap = consecutive; consecutive = nextConsecutive; nextConsecutive = swap;
        }
        int best = 0, col = rowStarts[m - 1];
        for (int i = col; i < n; i++) if (scores[i] >= best) { best = scores[i]; col = i; }
        if (indices) {
            positionCount = m;
            positions[m - 1] = start + col;
            for (int row = m - 1; row > 0;) {
                boolean matched = (trace[row * n + col] & 2) != 0;
                row--; col--;
                while (!matched) {
                    matched = (trace[row * n + col] & 1) != 0;
                    col--;
                }
                positions[row] = start + col;
            }
        }
        return best;
    }

    private void ensureCapacity(int n, int cells) {
        if (chars.length < n) {
            chars = new int[n]; bonuses = new int[n]; scores = new int[n]; nextScores = new int[n];
            consecutive = new int[n]; nextConsecutive = new int[n];
        }
        if (trace.length < cells) trace = new byte[cells];
    }
    private int normalized(int c) {
        if (normalize) c = NucleoUnicode.normalize(c);
        return ignoreCase ? NucleoUnicode.fold(c) : c;
    }
    private int classNormalized(int c, int cls) {
        if (c < 128) return ignoreCase && cls == 4 ? c + 32 : c;
        if (normalize) c = NucleoUnicode.normalize(c);
        return ignoreCase && (normalize || cls == 4) ? NucleoUnicode.fold(c) : c;
    }
    private int initialClass() { return options.boundaries == Boundaries.PATHS ? 2 : 0; }
    private int whiteBonus() { return options.boundaries == Boundaries.PATHS ? 8 : 10; }
    private int charClass(int c) {
        if (c >= 128) return NucleoUnicode.charClass(c);
        if (c >= 'a' && c <= 'z') return 3;
        if (c >= 'A' && c <= 'Z') return 4;
        if (c >= '0' && c <= '9') return 6;
        if (c == 9 || c == 10 || c == 12 || c == 13 || c == 32) return 0;
        if (c == '/' || c == ':' || (options.boundaries == Boundaries.DEFAULT && (c == ',' || c == ';' || c == '|'))) return 2;
        return 1;
    }
    private int bonus(int previous, int cls) {
        if (cls > 2) {
            if (previous == 0) return whiteBonus();
            if (previous == 2) return 9;
            if (previous == 1) return 8;
        }
        if (previous == 3 && cls == 4 || previous != 6 && cls == 6) return 5;
        if (cls == 0) return whiteBonus();
        return cls == 1 ? 8 : 0;
    }
    private static int[] scalarValues(String text, int limit) {
        int count = text.codePointCount(0, text.length());
        if (count > limit) throw new IllegalArgumentException("Codepoint limit exceeded: " + limit);
        int[] points = text.codePoints().toArray();
        for (int c : points) if (c >= 0xd800 && c <= 0xdfff)
            throw new IllegalArgumentException("Unpaired UTF-16 surrogate");
        return points;
    }
}
