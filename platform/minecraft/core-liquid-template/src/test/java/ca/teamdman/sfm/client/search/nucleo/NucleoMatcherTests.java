// SPDX-License-Identifier: MPL-2.0
package ca.teamdman.sfm.client.search.nucleo;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class NucleoMatcherTests {
    @Test void pinnedRustScoresAndPositions() {
        // Captured from the real Rust oracle at NucleoMatcher.UPSTREAM_REVISION.
        check("m:ii", "minecraft:iron_ingot", 85, List.of(0, 9, 10, 15));
        check("foo", "xf foo", 88, List.of(3, 4, 5));
        check("aa", "a_a_a", 57, List.of(0, 2));
        check("😀x", "a😀_x", 53, List.of(1, 3));
        check("e", "é", 36, List.of(0));
        check("ab", "a\u0301b", 57, List.of(0, 2));
    }
    private static void check(String query, String candidate, int score, List<Integer> indices) {
        var matcher = new NucleoMatcher(query);
        var prepared = new NucleoMatcher.Candidate(candidate);
        var result = matcher.match(prepared);
        assertNotNull(result);
        assertEquals(score, result.score());
        assertEquals(indices, result.codePointPositions());
        assertEquals(score, matcher.score(prepared));
    }
    @Test void utf16HighlightRangesNeverSplitSurrogates() {
        var match = new NucleoMatcher("😀x").match("a😀_x");
        assertEquals(List.of(new NucleoMatcher.Span(1, 3), new NucleoMatcher.Span(4, 5)), match.utf16Spans());
        assertThrows(UnsupportedOperationException.class, () -> match.codePointPositions().add(9));
    }
    @Test void explicitCaseNormalizationAndLiteralQueryContract() {
        assertNull(new NucleoMatcher("É").match("é"));
        assertNull(new NucleoMatcher("é").match("e"));
        assertNull(new NucleoMatcher("papar").match("paper"));
        assertNull(new NucleoMatcher("^foo").match("foo"));
        assertEquals(0, new NucleoMatcher("").match("").score());
        assertNull(new NucleoMatcher("aa").match("a"));
        assertThrows(IllegalArgumentException.class, () -> new NucleoMatcher("\ud800"));
        assertThrows(IllegalArgumentException.class, () -> new NucleoMatcher("a".repeat(2049)));
        assertThrows(IllegalArgumentException.class, () -> new NucleoMatcher.Candidate("a".repeat(65536)));
    }
    @Test void preservesDuplicatePayloadsAndPriorityTiers() {
        var entries = List.of(new NucleoSearch.Entry<>(new Object(), "minecraft:iron_ingot"),
                new NucleoSearch.Entry<>(new Object(), "minecraft:iron_ingot"));
        var result = new NucleoSearch<>(entries).search("ii", NucleoMatcher.Options.DEFAULT, 10, () -> false);
        assertSame(entries.get(0).value(), result.get(0).entry().value());
        assertSame(entries.get(1).value(), result.get(1).entry().value());
        var palette = new NucleoSearch<>(List.of(
                new NucleoSearch.Entry<>("context", new NucleoMatcher.Candidate("open focused file"), 0),
                new NucleoSearch.Entry<>("global", new NucleoMatcher.Candidate("open"), 1)));
        assertEquals("context", palette.search("open", NucleoMatcher.Options.DEFAULT, 1, () -> false).get(0).entry().value());
    }
    @Test void cancellationNeverPublishesPartialResults() {
        var search = new NucleoSearch<>(List.of(new NucleoSearch.Entry<>(0, "minecraft:iron_ingot"),
                new NucleoSearch.Entry<>(1, "create:iron_sheet")));
        AtomicInteger checks = new AtomicInteger();
        assertThrows(CancellationException.class, () -> search.search("i", NucleoMatcher.Options.DEFAULT, 2,
                () -> checks.incrementAndGet() >= 3));
        assertEquals(3, checks.get());
    }
}
