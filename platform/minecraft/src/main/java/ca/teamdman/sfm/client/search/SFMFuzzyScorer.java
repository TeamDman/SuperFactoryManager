package ca.teamdman.sfm.client.search;

import org.simmetrics.StringDistance;
import org.simmetrics.metrics.StringDistances;

import java.util.Locale;
import java.util.Objects;

/** Shared, deterministic fuzzy relevance used by palette and explorer projections. */
public final class SFMFuzzyScorer {
    public static final float DEFAULT_THRESHOLD = 0.65F;
    private static final StringDistance DISTANCE = StringDistances.damerauLevenshtein();

    private SFMFuzzyScorer() {
    }

    /** Lower scores are better. Values at or below {@link #DEFAULT_THRESHOLD} are matches. */
    public static float score(String query, String candidate) {
        return score(query, candidate, false);
    }

    /** Explicit case-sensitive variant; the historical two-argument ranking remains unchanged. */
    public static float score(String query, String candidate, boolean matchCase) {
        String normalizedQuery = matchCase ? Objects.requireNonNull(query).strip() : normalize(query);
        String normalizedCandidate = matchCase ? Objects.requireNonNull(candidate).strip() : normalize(candidate);
        if (normalizedQuery.isEmpty()) return 0F;
        if (normalizedCandidate.isEmpty()) return 1F;
        if (normalizedCandidate.equals(normalizedQuery)) return 0F;

        float distance = DISTANCE.distance(normalizedQuery, normalizedCandidate)
                / Math.max(1, Math.max(normalizedQuery.length(), normalizedCandidate.length()));
        if (normalizedCandidate.startsWith(normalizedQuery)) distance -= 0.05F;
        if (normalizedCandidate.contains(normalizedQuery)) distance -= 0.5F;
        return Math.min(distance, subsequenceScore(normalizedQuery, normalizedCandidate));
    }

    public static boolean matches(String query, String candidate) {
        return score(query, candidate) <= DEFAULT_THRESHOLD;
    }

    private static float subsequenceScore(String query, String candidate) {
        int candidateIndex = 0;
        int first = -1;
        int previous = -1;
        int gaps = 0;
        for (int queryIndex = 0; queryIndex < query.length(); queryIndex++) {
            char expected = query.charAt(queryIndex);
            while (candidateIndex < candidate.length() && candidate.charAt(candidateIndex) != expected) {
                candidateIndex++;
            }
            if (candidateIndex >= candidate.length()) return Float.POSITIVE_INFINITY;
            if (first < 0) first = candidateIndex;
            if (previous >= 0) gaps += candidateIndex - previous - 1;
            previous = candidateIndex++;
        }
        float lengthPenalty = (candidate.length() - query.length())
                / (float) Math.max(1, candidate.length());
        float gapPenalty = gaps / (float) Math.max(1, candidate.length());
        float startPenalty = first / (float) Math.max(1, candidate.length());
        return 0.10F + lengthPenalty * 0.20F + gapPenalty * 0.30F + startPenalty * 0.10F;
    }

    private static String normalize(String value) {
        return Objects.requireNonNull(value, "value").strip().toLowerCase(Locale.ROOT);
    }
}
