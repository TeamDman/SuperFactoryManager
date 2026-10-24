package ca.teamdman.sfm.client.search;

import java.util.List;
import java.util.Objects;
import ca.teamdman.sfm.client.search.nucleo.NucleoMatcher;

/** Bounded Explorer integration. Native ranking never changes tree order. One matcher per worker. */
public final class SFMExplorerNucleoAdapter {
    public static final int MAX_QUERY_CODEPOINTS = 1024, MAX_CANDIDATE_CODEPOINTS = 32768, MAX_FRAGMENTS = 4096;
    public static final long MAX_COMPARISONS = 1_000_000;
    public record Span(int start, int end) {
        public Span { if (start < 0 || end <= start) throw new IllegalArgumentException("Invalid original UTF-16 span"); }
    }
    public record RowMatch(boolean matches, List<Span> utf16Spans) {
        public RowMatch { utf16Spans = List.copyOf(utf16Spans); }
    }
    public static final class LimitExceeded extends IllegalArgumentException {
        public LimitExceeded(String message) { super(message); }
    }
    @FunctionalInterface public interface Budget { void spend(long operations); }
    public record NativeMatch(int score, List<Span> utf16Spans) {
        public NativeMatch { utf16Spans = List.copyOf(utf16Spans); }
    }
    @FunctionalInterface public interface Compiled { NativeMatch match(String candidate); }
    @FunctionalInterface public interface Factory {
        /** Bind Options(RESPECT/IGNORE, SMART, DEFAULT, false); Compiled.match returns null only for absent matches. */
        Compiled compile(String query, boolean matchCase);
    }
    private final Compiled matcher;
    private final int queryPoints;
    public static SFMExplorerNucleoAdapter compile(String query, boolean matchCase) {
        return new SFMExplorerNucleoAdapter(query, matchCase, (text, respectCase) -> {
            var nativeMatcher = new NucleoMatcher(text, new NucleoMatcher.Options(
                    respectCase ? NucleoMatcher.CaseMode.RESPECT : NucleoMatcher.CaseMode.IGNORE,
                    NucleoMatcher.Normalization.SMART, NucleoMatcher.Boundaries.DEFAULT, false));
            return candidate -> {
                var hit = nativeMatcher.match(candidate);
                return hit == null ? null : new NativeMatch(hit.score(), hit.utf16Spans().stream()
                        .map(span -> new Span(span.start(), span.end())).toList());
            };
        });
    }
    public SFMExplorerNucleoAdapter(String query, boolean matchCase, Factory factory) {
        Objects.requireNonNull(query, "query");
        if (factory == null) throw new IllegalStateException("Nucleo source/provider is not installed in this projection");
        if (query.length() > MAX_QUERY_CODEPOINTS * 2) throw new LimitExceeded("Query exceeds Explorer budget");
        queryPoints = query.codePointCount(0, query.length());
        if (queryPoints > MAX_QUERY_CODEPOINTS) throw new LimitExceeded("Query exceeds Explorer budget");
        matcher = Objects.requireNonNull(factory.compile(query, matchCase), "compiled Nucleo provider");
    }
    public RowMatch match(String candidate, Budget budget) {
        Objects.requireNonNull(candidate, "candidate"); Objects.requireNonNull(budget, "budget");
        budget.spend(0);
        if (candidate.length() > MAX_CANDIDATE_CODEPOINTS * 2)
            throw new LimitExceeded("Candidate exceeds Explorer budget");
        int points = candidate.codePointCount(0, candidate.length());
        long work = (long) Math.max(1, queryPoints) * points;
        if (points > MAX_CANDIDATE_CODEPOINTS || work > MAX_COMPARISONS)
            throw new LimitExceeded("Candidate exceeds Explorer matching budget");
        budget.spend(work);
        NativeMatch matched = matcher.match(candidate);
        budget.spend(0); // Recheck cancellation before the caller retains a row or publishes spans.
        if (matched == null) return new RowMatch(false, List.of());
        if (matched.score() < 0 || matched.utf16Spans().size() > MAX_FRAGMENTS
                || queryPoints > 0 && matched.utf16Spans().isEmpty())
            throw new IllegalArgumentException("Invalid Nucleo match result");
        int previous = 0;
        for (var span : matched.utf16Spans()) {
            if (span.start() < previous || span.end() > candidate.length()
                    || splitsSurrogate(candidate, span.start()) || splitsSurrogate(candidate, span.end()))
                throw new IllegalArgumentException("Nucleo spans must be ordered original-string UTF-16 boundaries");
            previous = span.end();
        }
        // Explorer row order is authoritative. Native ranking must not reorder this tree.
        return new RowMatch(true, matched.utf16Spans());
    }
    private static boolean splitsSurrogate(String text, int position) {
        return position > 0 && position < text.length() && Character.isHighSurrogate(text.charAt(position - 1))
                && Character.isLowSurrogate(text.charAt(position));
    }
}
