package ca.teamdman.sfm.client.search;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

class SFMRegexPatternTests {
    private static final SFMTextMatchOptions REGEX = SFMTextMatchOptions.defaults().toggleRegex();

    @Test void familiarGreedyLazyAlternationAndClassesAgreeWithTheSharedJavaSubset() {
        var patterns = List.of("a", "a|ab", "ab|a", "(a|ab)*b", "a*", "a*?", "a+?", "a??", "a{0,2}",
                "a{0,2}?", "a{2,}", "(?:a?)*b", "(a+)+b", "(?:ab|a)*", "[a-c]+", "[^a]+", "[\\d_-]+",
                "[a-zA-Z0-9]+", "\\w+", "\\d+", "\\s+", "\\bword\\b", "\\Boo\\B", "^a+$", "\\Aa*\\z",
                ".*?\\n\\n", "\\x61\\u0062", "[\\n\\t]+", "[\\]-]+", "a(?:|b)c", "(?:)*", "");
        var strings = List.of("", "a", "aaaa", "ab", "abc", "aab", "abbc", "word foo good", "12_A-b", "a\nb\n\n",
                "aaa\r\nb\r\n", "\n\n", "x\t \n", "]--", "ac", "abcabc", "ABC123");
        for (String pattern : patterns) for (String value : strings) for (boolean dotAll : List.of(false, true)) {
            var options = REGEX.withDotAll(dotAll);
            int flags = Pattern.MULTILINE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS | Pattern.CASE_INSENSITIVE;
            if (dotAll) flags |= Pattern.DOTALL;
            var expected = Pattern.compile(pattern, flags).matcher(value);
            var spans = new ArrayList<String>();
            while (expected.find()) spans.add(expected.start() + ":" + expected.end());
            var actual = SFMTextMatcher.compile(pattern, options).match(value);
            var actualSpans = new ArrayList<String>();
            actual.fragments().forEach(fragment -> actualSpans.add(fragment.start() + ":" + fragment.end()));
            actual.zeroWidthOffsets().forEach(offset -> actualSpans.add(offset + ":" + offset));
            actualSpans.sort(Comparator.comparingInt(span -> Integer.parseInt(span.split(":")[0])));
            assertEquals(spans, actualSpans, pattern + " against " + value.replace("\n", "\\n") + " dot-all=" + dotAll);
        }
    }

    @Test void dotAllSelectsParagraphsAcrossLinesAndHandlesCrlfExplicitly() {
        // Unlike Java's multiline ^, SFM includes the empty document/trailing empty line as a line.
        assertEquals(List.of(0), SFMTextMatcher.compile("^$", REGEX).match("").zeroWidthOffsets());
        assertEquals(List.of(0, 2), SFMTextMatcher.compile("^$", REGEX).match("\r\n").zeroWidthOffsets());
        String value = "first line\nsecond line\n\nthird\n\n";
        var match = SFMTextMatcher.compile(".*?\\n\\n", REGEX.withDotAll(true)).match(value);
        assertEquals(List.of(new SFMTextMatcher.Fragment(0, 24), new SFMTextMatcher.Fragment(24, 31)), match.fragments());
        assertEquals(List.of(new SFMTextMatcher.Fragment(11, 24), new SFMTextMatcher.Fragment(24, 31)),
                SFMTextMatcher.compile(".*?\\n\\n", REGEX).match(value).fragments());
        String crlf = "one\r\ntwo\r\n\r\nthree\r\n\r\n";
        assertEquals(List.of(new SFMTextMatcher.Fragment(0, 12), new SFMTextMatcher.Fragment(12, 21)),
                SFMTextMatcher.compile(".*?(?:\\r?\\n){2}", REGEX.withDotAll(true)).match(crlf).fragments());
    }

    @Test void unicodeFragmentsAndZeroWidthProgressUseCodePointsNotHalfSurrogates() {
        var emoji = SFMTextMatcher.compile(".", REGEX).match("😀a");
        assertEquals(List.of(new SFMTextMatcher.Fragment(0, 2), new SFMTextMatcher.Fragment(2, 3)), emoji.fragments());
        var empty = SFMTextMatcher.compile("", REGEX).match("😀a");
        assertTrue(empty.matches());
        assertTrue(empty.fragments().isEmpty());
        assertEquals(List.of(0, 2, 3), empty.zeroWidthOffsets());
        assertTrue(SFMTextMatcher.compile("ä+", REGEX).match("Ää").matches());
        assertFalse(SFMTextMatcher.compile("ä+", REGEX.withCase(true)).match("Ä").matches());
        assertTrue(SFMTextMatcher.compile("\\w+", REGEX).match("e\u0301_name").fragments().contains(new SFMTextMatcher.Fragment(0, 7)));
        assertEquals(List.of(new SFMTextMatcher.Fragment(10, 14)),
                SFMTextMatcher.compile("name", REGEX.withWholeWord(true)).match("name_more:name-other").fragments());
    }

    @Test void invalidUnsupportedAndOversizedProgramsAreExplicitErrors() {
        for (String pattern : List.of("(", "[", "[]", "[z-a]", "[a&&b]", "[\\d-a]", "\\1", "(?=a)", "(?<=a)",
                "(?<name>a)", "(?i)a", "\\p{L}", "a++", "a{3,2}", "a{257}", "\\uD800", "a{bad}")) {
            assertThrows(SFMTextMatcher.LimitExceeded.class, () -> SFMTextMatcher.compile(pattern, REGEX), pattern);
        }
        assertThrows(SFMTextMatcher.LimitExceeded.class, () -> SFMTextMatcher.compile("(".repeat(25) + "a" + ")".repeat(25), REGEX));
        assertThrows(SFMTextMatcher.LimitExceeded.class, () -> SFMTextMatcher.compile("(a{200}){200}", REGEX));
        assertThrows(SFMTextMatcher.LimitExceeded.class, () -> SFMTextMatcher.compile("(?:(?:){256}){256}", REGEX));
    }

    @Test void adversarialNonmatchStopsAtTheBudgetAndCancellationStopsInsideMatching() {
        var matcher = SFMTextMatcher.compile("(a|aa)*b", REGEX);
        var budget = new SFMMatchBudget(20_000);
        assertThrows(SFMTextMatcher.LimitExceeded.class, () -> matcher.match("a".repeat(2000), budget));
        assertTrue(budget.exhausted());
        assertTrue(budget.used() <= 20_000);
        long used = budget.used();
        assertThrows(SFMTextMatcher.LimitExceeded.class, () -> matcher.match("a", budget));
        assertEquals(used, budget.used());
        var checks = new AtomicInteger();
        var cancelled = new SFMMatchBudget(1_000_000, () -> checks.incrementAndGet() > 100);
        assertThrows(CancellationException.class, () -> matcher.match("a".repeat(1000), cancelled));
        assertTrue(checks.get() <= 101);
        assertFalse(cancelled.exhausted());
        assertFalse(SFMTextMatcher.compile("(a+)+b", REGEX).match("a".repeat(50)).matches());
        assertTrue(SFMTextMatcher.compile("(?:a?)*", REGEX).match("a".repeat(200)).matches());
    }
}
