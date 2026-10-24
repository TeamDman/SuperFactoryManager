package ca.teamdman.sfm.client.search;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class SFMTextMatcherTests {
    @Test void nucleoIsExplicitKeepsOriginalSpansAndDoesNotExposeRanking() {
        var options = SFMTextMatchOptions.defaults().toggleNucleo();
        var matcher = SFMTextMatcher.compile("😀e", options);
        assertEquals(List.of(new SFMTextMatcher.Fragment(1, 3), new SFMTextMatcher.Fragment(4, 5)),
                matcher.match("a😀_é").fragments());
        assertEquals(0, matcher.match("😀e").score());
        assertFalse(SFMTextMatcher.compile("papar", options).match("paper").matches());
        assertTrue(SFMTextMatcher.compile("E", options).match("e").matches());
        assertFalse(SFMTextMatcher.compile("E", options.withCase(true)).match("e").matches());
        assertThrows(SFMTextMatcher.LimitExceeded.class,
                () -> SFMTextMatcher.compile("e", options.withWholeWord(true)));
        assertThrows(SFMTextMatcher.LimitExceeded.class, () -> matcher.match("\ud800"));
        assertThrows(java.util.concurrent.CancellationException.class,
                () -> matcher.match("😀e", new SFMMatchBudget(1000, () -> true)));
    }
    @Test void literalDefaultDoesNotInventSimilarFilenameMatches() {
        String query = "ExploreReviewInteractivelyPuppetAction.java";
        var literal = SFMTextMatcher.compile(query, SFMTextMatchOptions.defaults());
        assertTrue(literal.match(query).matches());
        assertFalse(literal.match("ExerciseExplorerInteractionFidelityPuppetAction.java").matches());
        assertFalse(literal.match("ReleaseReviewJourneyPuppetAction.java").matches());
    }

    @Test void explicitFuzzyKeepsTheExistingRankAndSupportsExactFragments() {
        var options = SFMTextMatchOptions.legacyFuzzy();
        for (String candidate : List.of("PuppetAction.java", "ExercisePuppetAction.java", "not related", "pa")) {
            float score = SFMFuzzyScorer.score("pa", candidate);
            var result = SFMTextMatcher.compile("pa", options).match(candidate);
            assertEquals(score <= SFMFuzzyScorer.DEFAULT_THRESHOLD, result.matches());
            if (result.matches()) assertEquals(score, result.score());
        }
        var result = SFMTextMatcher.compile("PA", options).match("PuppetAction");
        assertEquals(List.of(new SFMTextMatcher.Fragment(0, 1), new SFMTextMatcher.Fragment(6, 7)), result.fragments());
        assertFalse(result.approximate());
    }

    @Test void typoOnlyFuzzyMatchIsExplicitlyApproximate() {
        var result = SFMTextMatcher.compile("papar", SFMTextMatchOptions.legacyFuzzy()).match("paper");
        assertTrue(result.matches());
        assertTrue(result.approximate());
        assertEquals(List.of(new SFMTextMatcher.Fragment(0, 5)), result.fragments());
    }

    @Test void literalFragmentsUseOriginalUtf16AndKeepAdjacentOccurrencesSeparate() {
        var matcher = SFMTextMatcher.compile("😀é", SFMTextMatchOptions.defaults());
        assertEquals(List.of(new SFMTextMatcher.Fragment(1, 4), new SFMTextMatcher.Fragment(4, 7)),
                matcher.match("x😀é😀É").fragments());
        assertFalse(SFMTextMatcher.compile("😀é", SFMTextMatchOptions.defaults().withCase(true)).match("😀É").matches());
        assertEquals(List.of(new SFMTextMatcher.Fragment(0, 2), new SFMTextMatcher.Fragment(2, 4)),
                SFMTextMatcher.compile("hi", SFMTextMatchOptions.defaults()).match("hihi").fragments());
    }

    @Test void wholeWordTreatsColonSlashHyphenAsBoundariesButNotUnderscoresAndMarks() {
        var matcher = SFMTextMatcher.compile("path", SFMTextMatchOptions.defaults().withWholeWord(true));
        assertEquals(3, matcher.match("path:path/path-path_suffix").fragments().size());
        assertFalse(matcher.match("xpath").matches());
        assertFalse(matcher.match("path\u0301").matches());
        assertTrue(matcher.match("path.json").matches());
        assertFalse(SFMTextMatcher.compile("paper", SFMTextMatchOptions.legacyFuzzy().withWholeWord(true))
                .match("papermachinery").matches());
    }

    @Test void optionsAreIndependentAndSpecialModesAreMutuallyExclusive() {
        var find = SFMTextMatchOptions.defaults().withCase(true).withWholeWord(true).withDotAll(true);
        var filter = SFMTextMatchOptions.defaults();
        assertEquals(SFMTextMatchOptions.Mode.FUZZY, find.toggleFuzzy().mode());
        assertEquals(SFMTextMatchOptions.Mode.REGEX, find.toggleFuzzy().toggleRegex().mode());
        assertEquals(SFMTextMatchOptions.Mode.LITERAL, find.toggleRegex().toggleRegex().mode());
        assertTrue(find.toggleFuzzy().matchCase());
        assertTrue(find.toggleFuzzy().wholeWord());
        assertTrue(find.toggleFuzzy().dotAll());
        assertFalse(filter.matchCase());
        assertEquals(SFMTextMatchOptions.Mode.LITERAL, filter.mode());
    }

    @Test void limitsAndInvalidRegexAreNeverSilentlyReportedAsNoMatches() {
        assertThrows(SFMTextMatcher.LimitExceeded.class,
                () -> SFMTextMatcher.compile("x".repeat(1025), SFMTextMatchOptions.defaults()));
        assertThrows(SFMTextMatcher.LimitExceeded.class,
                () -> SFMTextMatcher.compile("x", SFMTextMatchOptions.defaults()).match("x".repeat(32769)));
        assertThrows(SFMTextMatcher.LimitExceeded.class,
                () -> SFMTextMatcher.compile("x".repeat(1024), SFMTextMatchOptions.defaults()).match("y".repeat(1000)));
        assertThrows(SFMTextMatcher.LimitExceeded.class,
                () -> SFMTextMatcher.compile("x", SFMTextMatchOptions.defaults()).match("x".repeat(4097)));
        assertThrows(SFMTextMatcher.LimitExceeded.class,
                () -> SFMTextMatcher.compile("(?=x)", SFMTextMatchOptions.defaults().toggleRegex()));
    }

    @Test void simpleCaseFoldingDoesNotDependOnMachineLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"));
            assertTrue(SFMTextMatcher.compile("FILE", SFMTextMatchOptions.defaults()).match("file").matches());
            assertTrue(SFMTextMatcher.compile("\uD801\uDC00", SFMTextMatchOptions.defaults()).match("\uD801\uDC28").matches());
        } finally { Locale.setDefault(previous); }
    }
}
