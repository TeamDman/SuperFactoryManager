/* SPDX-License-Identifier: MPL-2.0
 * SFM batch adapter for the Nucleo matcher port. See scripts/nucleo-parity/README.md.
 */
package ca.teamdman.sfm.client.search.nucleo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/** Immutable eligible-candidate snapshot. Call search on a worker; this class schedules no threads. */
public final class NucleoSearch<T> {
    /** Value is the original action/payload. Lower priority tiers sort first, before fuzzy score. */
    public record Entry<T>(T value, NucleoMatcher.Candidate candidate, int priority) {
        public Entry { Objects.requireNonNull(candidate); }
        public Entry(T value, String displayText) { this(value, new NucleoMatcher.Candidate(displayText), 0); }
    }
    /** Input index preserves identity and stable tie order, even for duplicate display strings. */
    public record Result<T>(Entry<T> entry, int inputIndex, NucleoMatcher.Match match) {}
    private record Scored(int index, int priority, int score) {}
    private static final Comparator<Scored> ORDER = Comparator.comparingInt(Scored::priority)
            .thenComparing(Comparator.comparingInt(Scored::score).reversed()).thenComparingInt(Scored::index);
    private final List<Entry<T>> entries;

    public NucleoSearch(List<Entry<T>> eligibleEntries) { entries = List.copyOf(eligibleEntries); }
    public int size() { return entries.size(); }

    /**
     * Returns up to limit matches, ordered by priority, descending score, then original input order.
     * Empty query matches everything at score zero, preserving input order within each tier.
     * Cancellation throws and never returns a partial result. The caller must also reject stale
     * generations on its UI thread before publishing, after this method has returned.
     */
    public List<Result<T>> search(String query, NucleoMatcher.Options options, int limit,
                                  BooleanSupplier cancelled) {
        Objects.requireNonNull(cancelled);
        if (limit < 0) throw new IllegalArgumentException("Negative result limit");
        var matcher = new NucleoMatcher(query, options);
        checkCancelled(cancelled);
        if (limit == 0 || entries.isEmpty()) return List.of();
        int capacity = Math.min(limit, entries.size());
        var best = new PriorityQueue<Scored>(Math.min(capacity, 256), ORDER.reversed());
        for (int i = 0; i < entries.size(); i++) {
            checkCancelled(cancelled);
            Entry<T> entry = entries.get(i);
            int score = matcher.score(entry.candidate);
            if (score < 0) continue;
            var result = new Scored(i, entry.priority, score);
            if (best.size() < capacity) best.add(result);
            else if (ORDER.compare(result, best.peek()) < 0) { best.poll(); best.add(result); }
        }
        checkCancelled(cancelled);
        var ordered = new ArrayList<>(best);
        ordered.sort(ORDER);
        var results = new ArrayList<Result<T>>(ordered.size());
        for (Scored item : ordered) {
            checkCancelled(cancelled);
            Entry<T> entry = entries.get(item.index);
            results.add(new Result<>(entry, item.index, matcher.match(entry.candidate)));
        }
        checkCancelled(cancelled);
        return List.copyOf(results);
    }
    private static void checkCancelled(BooleanSupplier cancelled) {
        if (Thread.currentThread().isInterrupted() || cancelled.getAsBoolean()) throw new CancellationException();
    }
}
