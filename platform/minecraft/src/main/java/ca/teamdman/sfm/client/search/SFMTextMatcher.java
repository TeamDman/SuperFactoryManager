package ca.teamdman.sfm.client.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Bounded matching evidence in the original string's UTF-16 coordinates. */
public final class SFMTextMatcher {
    public static final int MAX_QUERY_CODEPOINTS = 1024;
    public static final int MAX_CANDIDATE_CODEPOINTS = 32768;
    public static final int MAX_FRAGMENTS = 4096;
    public static final long MAX_COMPARISONS = 1_000_000;
    public record Fragment(int start, int end) {
        public Fragment { if (start < 0 || end <= start) throw new IllegalArgumentException("Invalid match fragment"); }
        public boolean contains(int offset) { return offset >= start && offset < end; }
    }
    public record Match(boolean matches, float score, List<Fragment> fragments, boolean approximate,
                        List<Integer> zeroWidthOffsets) {
        public Match { fragments = List.copyOf(fragments); zeroWidthOffsets = List.copyOf(zeroWidthOffsets); }
        public Match(boolean matches, float score, List<Fragment> fragments, boolean approximate) {
            this(matches, score, fragments, approximate, List.of());
        }
        public static Match absent() { return new Match(false, Float.POSITIVE_INFINITY, List.of(), false); }
    }
    /** Callers publish incomplete/unsupported evidence; catching this must not become an empty match set. */
    public static final class LimitExceeded extends IllegalArgumentException {
        public LimitExceeded(String message) { super(message); }
    }
    private final String query;
    private final int[] queryPoints;
    private final SFMTextMatchOptions options;
    private final SFMRegexPattern regex;
    private SFMTextMatcher(String query, SFMTextMatchOptions options) {
        this.query = Objects.requireNonNull(query);
        this.options = Objects.requireNonNull(options);
        if (query.length() > MAX_QUERY_CODEPOINTS * 2) throw new LimitExceeded("Query is too long");
        this.queryPoints = query.codePoints().toArray();
        if (queryPoints.length > MAX_QUERY_CODEPOINTS) throw new LimitExceeded("Query exceeds code-point limit");
        regex = options.mode() == SFMTextMatchOptions.Mode.REGEX ? new SFMRegexPattern(query, options) : null;
    }
    public static SFMTextMatcher compile(String query, SFMTextMatchOptions options) { return new SFMTextMatcher(query, options); }
    public Match match(String candidate) {
        return match(candidate, new SFMMatchBudget(MAX_COMPARISONS));
    }
    public Match match(String candidate, SFMMatchBudget budget) {
        Objects.requireNonNull(candidate);
        budget.spend(0); // Do not allocate candidate arrays after a query was cancelled/exhausted.
        if (candidate.length() > MAX_CANDIDATE_CODEPOINTS * 2) throw new LimitExceeded("Candidate is too long");
        int[] points = candidate.codePoints().toArray();
        if (points.length > MAX_CANDIDATE_CODEPOINTS) throw new LimitExceeded("Candidate exceeds code-point limit");
        if (regex != null) {
            budget.spend(points.length);
            int[] offsets = offsets(points);
            var occurrences = regex.find(points, budget);
            return new Match(!occurrences.isEmpty(), occurrences.isEmpty() ? Float.POSITIVE_INFINITY : 0,
                    occurrences.stream().filter(span -> span.end() > span.start())
                            .map(span -> new Fragment(offsets[span.start()], offsets[span.end()])).toList(), false,
                    occurrences.stream().filter(span -> span.end() == span.start()).map(span -> offsets[span.start()]).toList());
        }
        if ((long) Math.max(1, queryPoints.length) * points.length > MAX_COMPARISONS) {
            throw new LimitExceeded("Match exceeds comparison budget");
        }
        budget.spend(points.length);
        if (queryPoints.length == 0) return new Match(true, 0, List.of(), false);
        int[] offsets = offsets(points);
        if (options.mode() == SFMTextMatchOptions.Mode.LITERAL) return literal(points, offsets, budget);
        budget.spend((long) queryPoints.length * points.length);
        return fuzzy(candidate, points, offsets);
    }
    private Match literal(int[] points, int[] offsets, SFMMatchBudget budget) {
        var fragments = new ArrayList<Fragment>();
        for (int start = 0; start + queryPoints.length <= points.length; start++) {
            budget.spend(1);
            int end = start + queryPoints.length;
            if (options.wholeWord() && !wordBoundaries(points, start, end)) continue;
            boolean same = true;
            for (int i = 0; i < queryPoints.length; i++) {
                budget.spend(1);
                if (!equal(points[start + i], queryPoints[i])) { same = false; break; }
            }
            if (same) {
                if (fragments.size() == MAX_FRAGMENTS) throw new LimitExceeded("Match exceeds fragment budget");
                fragments.add(new Fragment(offsets[start], offsets[end]));
                start = end - 1; // Standard non-overlapping occurrences; regex zero-width policy is separate.
            }
        }
        return fragments.isEmpty() ? Match.absent() : new Match(true, 0, fragments, false);
    }
    private Match fuzzy(String candidate, int[] points, int[] offsets) {
        if (options.wholeWord() && java.util.Arrays.stream(queryPoints).allMatch(SFMTextMatcher::wordMember)) {
            var fragments = new ArrayList<Fragment>();
            float best = Float.POSITIVE_INFINITY;
            boolean approximate = false;
            for (int start = 0; start < points.length;) {
                if (!wordMember(points[start])) { start++; continue; }
                int end = start + 1;
                while (end < points.length && wordMember(points[end])) end++;
                Match token = fuzzyField(candidate.substring(offsets[start], offsets[end]));
                if (token.matches()) {
                    best = Math.min(best, token.score());
                    approximate |= token.approximate();
                    for (Fragment fragment : token.fragments()) add(fragments,
                            new Fragment(offsets[start] + fragment.start(), offsets[start] + fragment.end()));
                }
                start = end;
            }
            return fragments.isEmpty() ? Match.absent() : new Match(true, best, fragments, approximate);
        }
        return fuzzyField(candidate);
    }
    private Match fuzzyField(String candidate) {
        float score = SFMFuzzyScorer.score(query, candidate, options.matchCase());
        if (score > SFMFuzzyScorer.DEFAULT_THRESHOLD) return Match.absent();
        int[] points = candidate.codePoints().toArray();
        int[] offsets = offsets(points);
        var fragments = new ArrayList<Fragment>();
        int position = 0;
        for (int expected : queryPoints) {
            while (position < points.length && !equal(points[position], expected)) position++;
            if (position == points.length) {
                return candidate.isEmpty() ? Match.absent()
                        : new Match(true, score, List.of(new Fragment(0, candidate.length())), true);
            }
            add(fragments, new Fragment(offsets[position], offsets[position + 1]));
            position++;
        }
        if (options.wholeWord() && !fragments.isEmpty()) {
            int first = fragments.get(0).start();
            int last = fragments.get(fragments.size() - 1).end();
            if ((first > 0 && wordMember(candidate.codePointBefore(first)))
                    || (last < candidate.length() && wordMember(candidate.codePointAt(last)))) return Match.absent();
        }
        return new Match(true, score, fragments, false);
    }
    private boolean equal(int left, int right) {
        return left == right || (!options.matchCase() && fold(left) == fold(right));
    }
    private static int fold(int point) { return Character.toLowerCase(Character.toUpperCase(point)); }
    public static boolean wordMember(int point) {
        int type = Character.getType(point);
        return point == '_' || Character.isLetterOrDigit(point) || type == Character.NON_SPACING_MARK
                || type == Character.COMBINING_SPACING_MARK || type == Character.ENCLOSING_MARK;
    }
    private static boolean wordBoundaries(int[] points, int start, int end) {
        return (start == 0 || !wordMember(points[start - 1])) && (end == points.length || !wordMember(points[end]));
    }
    private static int[] offsets(int[] points) {
        int[] result = new int[points.length + 1];
        for (int i = 0; i < points.length; i++) result[i + 1] = result[i] + Character.charCount(points[i]);
        return result;
    }
    private static void add(ArrayList<Fragment> fragments, Fragment fragment) {
        if (!fragments.isEmpty() && fragments.get(fragments.size() - 1).end() == fragment.start()) {
            Fragment previous = fragments.remove(fragments.size() - 1);
            fragments.add(new Fragment(previous.start(), fragment.end()));
        } else {
            if (fragments.size() == MAX_FRAGMENTS) throw new LimitExceeded("Match exceeds fragment budget");
            fragments.add(fragment);
        }
    }
}
