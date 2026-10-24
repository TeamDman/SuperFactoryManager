// SPDX-License-Identifier: MPL-2.0
import ca.teamdman.sfm.client.search.nucleo.NucleoMatcher;
import ca.teamdman.sfm.client.search.nucleo.NucleoSearch;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Dependency-free behavioral checks; run with -ea (also throws explicitly). */
public class CoreChecks {
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    static void rejected(Runnable run) {
        try { run.run(); throw new AssertionError("accepted invalid input"); }
        catch (IllegalArgumentException expected) { }
    }
    public static void main(String[] args) throws Exception {
        var defaults = NucleoMatcher.Options.DEFAULT;
        var match = new NucleoMatcher("😀x").match("a😀_x");
        check(match.codePointPositions().equals(List.of(1, 3)), "supplementary positions");
        check(match.utf16Spans().equals(List.of(new NucleoMatcher.Span(1, 3), new NucleoMatcher.Span(4, 5))), "UTF-16");
        check(new NucleoMatcher("foo").match("xf foo").codePointPositions().equals(List.of(3, 4, 5)), "optimal match");
        check(new NucleoMatcher("papar").match("paper") == null, "not edit distance");
        check(new NucleoMatcher("é").match("e") == null, "smart normalization asymmetry");
        check(new NucleoMatcher("e").match("é") != null, "smart normalization");
        check(new NucleoMatcher("E").match("e") == null, "smart case");
        check(new NucleoMatcher("ab").match("a\u0301b").codePointPositions().equals(List.of(0, 2)), "codepoints not graphemes");
        rejected(() -> new NucleoMatcher("\ud800"));
        rejected(() -> new NucleoMatcher.Candidate("\udc00"));
        rejected(() -> new NucleoMatcher("a".repeat(2049)));
        rejected(() -> new NucleoMatcher.Candidate("a".repeat(65536)));
        check(new NucleoMatcher("a".repeat(2048)).match("a".repeat(2048)).score() == 53258, "maximum exact score");

        Object action1 = new Object(), action2 = new Object(), action3 = new Object();
        var entries = List.of(new NucleoSearch.Entry<>(action1, "minecraft:iron_ingot"),
                new NucleoSearch.Entry<>(action2, "minecraft:iron_ingot"),
                new NucleoSearch.Entry<>(action3, "create:iron_sheet"));
        var search = new NucleoSearch<>(entries);
        var results = search.search("m:ii", defaults, 20, () -> false);
        check(results.size() == 2, "eligible matches");
        check(results.get(0).entry().value() == action1 && results.get(1).entry().value() == action2, "identity/tie ordering");
        check(results.get(0).entry().candidate().text().equals("minecraft:iron_ingot"), "display text retained");
        check(search.search("", defaults, 20, () -> false).stream().map(NucleoSearch.Result::inputIndex).toList()
                .equals(List.of(0, 1, 2)), "empty order");
        check(search.search("i", defaults, 1, () -> false).size() == 1, "top K");
        check(search.search("i", defaults, 0, () -> false).isEmpty(), "zero limit");

        // Contextual palette tiers remain stronger than fuzzy relevance.
        var palette = new NucleoSearch<>(List.of(
                new NucleoSearch.Entry<>("context action", new NucleoMatcher.Candidate("open focused file"), 0),
                new NucleoSearch.Entry<>("global action", new NucleoMatcher.Candidate("open"), 1)));
        check(palette.search("open", defaults, 10, () -> false).get(0).entry().value().equals("context action"), "priority tiers");

        // Top-K equals a complete stable sort, not merely a plausible set of candidates.
        var random = new Random(0x53464d4e);
        var many = new ArrayList<NucleoSearch.Entry<Integer>>();
        for (int i = 0; i < 10000; i++) many.add(new NucleoSearch.Entry<>(i,
                new NucleoMatcher.Candidate("mod_" + random.nextInt(30) + ":iron_" + random.nextInt(400)), i % 3));
        var all = new NucleoSearch<>(many);
        var full = all.search("i", defaults, 10000, () -> false);
        for (int limit : new int[]{1, 10, 50, 10001}) check(all.search("i", defaults, limit, () -> false)
                .equals(full.subList(0, Math.min(limit, full.size()))), "top K ordering " + limit);

        AtomicInteger checked = new AtomicInteger();
        try {
            all.search("i", defaults, 50, () -> checked.incrementAndGet() == 101);
            throw new AssertionError("cancelled search returned results");
        } catch (CancellationException expected) { check(checked.get() == 101, "prompt cancellation"); }
        Thread.currentThread().interrupt();
        try { search.search("", defaults, 10, () -> false); throw new AssertionError("ignored interrupt"); }
        catch (CancellationException expected) { Thread.interrupted(); }

        // Deterministic worker/publication model: old query completes, new generation wins before UI applies it.
        AtomicInteger generation = new AtomicInteger(1);
        int captured = generation.get();
        var old = search.search("iron", defaults, 10, () -> generation.get() != captured);
        generation.incrementAndGet();
        check(!old.isEmpty() && captured != generation.get(), "caller must reject stale publication");
        try { search.search("iron", defaults, 10, () -> generation.get() != captured); throw new AssertionError("stale job ran"); }
        catch (CancellationException expected) { }
        System.out.println("CoreChecks: PASS (mapping, limits, identity, tiers, top-K, cancellation, stale-generation model)");
    }
}
