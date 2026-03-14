package ca.teamdman.sfml;

import ca.teamdman.langs.SFMLParser;
import ca.teamdman.sfml.ast.NumberExpression;
import ca.teamdman.sfml.ast.NumberRange;
import ca.teamdman.sfml.ast.NumberSet;
import ca.teamdman.sfml.program_builder.SFMLProgramBuilder;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SfmNumberSetTests {

    private static @NotNull NumberSet parseSet(String programString) {
        return new SFMLProgramBuilder(programString)
                .<NumberSet>build(SFMLParser::numberSet).unwrapProgram();
    }

    private static NumberRange range(int start, int end) {
        return new NumberRange(
                NumberExpression.fromLiteral(start),
                NumberExpression.fromLiteral(end)
        );
    }

    private static NumberRange range(int value) {
        return range(value, value);
    }

    @Nested
    class NumberRangeTests {

        @Test
        public void containsValueInRange() {
            NumberRange range = range(5, 10);
            assertTrue(range.contains(5));
            assertTrue(range.contains(7));
            assertTrue(range.contains(10));
        }

        @Test
        public void doesNotContainValueOutsideRange() {
            NumberRange range = range(5, 10);
            assertFalse(range.contains(4));
            assertFalse(range.contains(11));
        }

        @Test
        public void singleValueRange() {
            NumberRange range = range(5);
            assertTrue(range.contains(5));
            assertFalse(range.contains(4));
            assertFalse(range.contains(6));
        }

        @Test
        public void compactAdjacentRanges() {
            NumberRange[] input = {range(1, 3), range(4, 6)};
            NumberRange[] result = NumberRange.compactRanges(input);
            assertEquals(1, result.length);
            assertEquals(1, result[0].start().value());
            assertEquals(6, result[0].end().value());
        }

        @Test
        public void compactOverlappingRanges() {
            NumberRange[] input = {range(1, 5), range(3, 8)};
            NumberRange[] result = NumberRange.compactRanges(input);
            assertEquals(1, result.length);
            assertEquals(1, result[0].start().value());
            assertEquals(8, result[0].end().value());
        }

        @Test
        public void compactDisjointRanges() {
            NumberRange[] input = {range(1, 3), range(5, 7)};
            NumberRange[] result = NumberRange.compactRanges(input);
            assertEquals(2, result.length);
            assertEquals(1, result[0].start().value());
            assertEquals(3, result[0].end().value());
            assertEquals(5, result[1].start().value());
            assertEquals(7, result[1].end().value());
        }

        @Test
        public void compactUnsortedRanges() {
            NumberRange[] input = {range(10, 12), range(1, 3), range(5, 7)};
            NumberRange[] result = NumberRange.compactRanges(input);
            assertEquals(3, result.length);
            assertEquals(1, result[0].start().value());
            assertEquals(5, result[1].start().value());
            assertEquals(10, result[2].start().value());
        }

        @Test
        public void compactEmptyArray() {
            NumberRange[] result = NumberRange.compactRanges(new NumberRange[]{});
            assertEquals(0, result.length);
        }

        @Test
        public void compactSingleRange() {
            NumberRange[] input = {range(5, 10)};
            NumberRange[] result = NumberRange.compactRanges(input);
            assertEquals(1, result.length);
            assertEquals(5, result[0].start().value());
            assertEquals(10, result[0].end().value());
        }

        @Test
        public void compactContainedRange() {
            // Range 3-5 is contained within 1-10
            NumberRange[] input = {range(1, 10), range(3, 5)};
            NumberRange[] result = NumberRange.compactRanges(input);
            assertEquals(1, result.length);
            assertEquals(1, result[0].start().value());
            assertEquals(10, result[0].end().value());
        }

        @Test
        public void compactManyConsecutiveValues() {
            // 1,2,3,4,5 should become 1-5
            NumberRange[] input = {range(1), range(2), range(3), range(4), range(5)};
            NumberRange[] result = NumberRange.compactRanges(input);
            assertEquals(1, result.length);
            assertEquals(1, result[0].start().value());
            assertEquals(5, result[0].end().value());
        }
    }

    @Nested
    class NumberSetContainsTests {

        @Test
        public void containsSingleValue() {
            NumberSet set = NumberSet.of(5);
            assertTrue(set.contains(5));
            assertFalse(set.contains(4));
            assertFalse(set.contains(6));
        }

        @Test
        public void containsMultipleValues() {
            NumberSet set = NumberSet.of(1, 3, 5);
            assertTrue(set.contains(1));
            assertTrue(set.contains(3));
            assertTrue(set.contains(5));
            assertFalse(set.contains(2));
            assertFalse(set.contains(4));
        }

        @Test
        public void containsRange() {
            NumberSet set = NumberSet.of(range(5, 10));
            assertTrue(set.contains(5));
            assertTrue(set.contains(7));
            assertTrue(set.contains(10));
            assertFalse(set.contains(4));
            assertFalse(set.contains(11));
        }

        @Test
        public void containsWithExclusions() {
            NumberSet set = NumberSet.of(
                    new NumberRange[]{range(1, 10)},
                    new NumberRange[]{range(5, 6)}
            );
            assertTrue(set.contains(1));
            assertTrue(set.contains(4));
            assertFalse(set.contains(5));
            assertFalse(set.contains(6));
            assertTrue(set.contains(7));
            assertTrue(set.contains(10));
        }

        @Test
        public void exclusionTakesPrecedence() {
            // Exclusions are checked first
            NumberSet set = NumberSet.of(
                    new NumberRange[]{range(1, 10)},
                    new NumberRange[]{range(1, 10)}
            );
            assertFalse(set.contains(5));
        }
    }

    @Nested
    class NumberSetOfFactoryTests {

        @Test
        public void ofSingleInt() {
            NumberSet set = NumberSet.of(5);
            assertEquals(1, set.ranges().length);
            assertEquals(5, set.ranges()[0].start().value());
            assertEquals(5, set.ranges()[0].end().value());
        }

        @Test
        public void ofMultipleIntsCompacts() {
            // 1,2,3 should be compacted to 1-3
            NumberSet set = NumberSet.of(1, 2, 3);
            assertEquals(1, set.ranges().length);
            assertEquals(1, set.ranges()[0].start().value());
            assertEquals(3, set.ranges()[0].end().value());
        }

        @Test
        public void ofDisjointInts() {
            NumberSet set = NumberSet.of(1, 5, 10);
            assertEquals(3, set.ranges().length);
        }

        @Test
        public void ofSingleRange() {
            NumberSet set = NumberSet.of(range(5, 10));
            assertEquals(1, set.ranges().length);
            assertEquals(5, set.ranges()[0].start().value());
            assertEquals(10, set.ranges()[0].end().value());
        }

        @Test
        public void ofRangeArrayCompacts() {
            NumberRange[] ranges = {range(1, 3), range(4, 6)};
            NumberSet set = NumberSet.of(ranges);
            assertEquals(1, set.ranges().length);
            assertEquals(1, set.ranges()[0].start().value());
            assertEquals(6, set.ranges()[0].end().value());
        }

        @Test
        public void ofWithExclusionsCompacts() {
            NumberRange[] ranges = {range(1, 3), range(4, 6)};
            NumberRange[] exclusions = {range(2), range(3)};
            NumberSet set = NumberSet.of(ranges, exclusions);
            assertEquals(1, set.ranges().length);
            assertEquals(1, set.exclusions().length);
            assertEquals(2, set.exclusions()[0].start().value());
            assertEquals(3, set.exclusions()[0].end().value());
        }
    }

    @Nested
    class NumberSetCompactTests {

        @Test
        public void compactAlreadyCompact() {
            NumberSet set = new NumberSet(
                    new NumberRange[]{range(1, 5)},
                    new NumberRange[]{}
            );
            NumberSet compacted = set.compact();
            assertEquals(1, compacted.ranges().length);
            assertEquals(1, compacted.ranges()[0].start().value());
            assertEquals(5, compacted.ranges()[0].end().value());
        }

        @Test
        public void compactMergesRanges() {
            NumberSet set = new NumberSet(
                    new NumberRange[]{range(1, 3), range(4, 6)},
                    new NumberRange[]{}
            );
            NumberSet compacted = set.compact();
            assertEquals(1, compacted.ranges().length);
            assertEquals(1, compacted.ranges()[0].start().value());
            assertEquals(6, compacted.ranges()[0].end().value());
        }

        @Test
        public void compactMergesExclusions() {
            NumberSet set = new NumberSet(
                    new NumberRange[]{range(1, 10)},
                    new NumberRange[]{range(3), range(4), range(5)}
            );
            NumberSet compacted = set.compact();
            assertEquals(1, compacted.exclusions().length);
            assertEquals(3, compacted.exclusions()[0].start().value());
            assertEquals(5, compacted.exclusions()[0].end().value());
        }
    }

    @Nested
    class NumberSetParsingTests {

        @Test
        public void parseSingleValue() {
            NumberSet parsed = parseSet("1");
            assertEquals(1, parsed.ranges().length);
            assertEquals(1, parsed.ranges()[0].start().value());
            assertEquals(1, parsed.ranges()[0].end().value());
        }

        @Test
        public void parseMultipleValues() {
            NumberSet parsed = parseSet("1,2,3");
            // Compacted to 1-3
            assertEquals(1, parsed.ranges().length);
            assertEquals(1, parsed.ranges()[0].start().value());
            assertEquals(3, parsed.ranges()[0].end().value());
        }

        @Test
        public void parseInclusiveRange() {
            NumberSet parsed = parseSet("1 TO 5");
            assertEquals(1, parsed.ranges().length);
            assertEquals(1, parsed.ranges()[0].start().value());
            assertEquals(5, parsed.ranges()[0].end().value());
        }

        @Test
        public void parseMixedValuesAndRanges() {
            NumberSet parsed = parseSet("1,2,3,4 TO 6");
            // Should compact to 1-6
            assertEquals(1, parsed.ranges().length);
            assertEquals(1, parsed.ranges()[0].start().value());
            assertEquals(6, parsed.ranges()[0].end().value());
        }

        @Test
        public void parseDisjointRanges() {
            NumberSet parsed = parseSet("1 TO 3,10 TO 15");
            assertEquals(2, parsed.ranges().length);
        }
    }

    @Nested
    class NumberSetToStringTests {

        @Test
        public void toStringSingleValue() {
            NumberSet set = NumberSet.of(5);
            assertEquals("5", set.toString());
        }

        @Test
        public void toStringRange() {
            NumberSet set = NumberSet.of(range(1, 5));
            assertEquals("1 TO 5", set.toString());
        }

        @Test
        public void toStringMultipleRanges() {
            NumberSet set = new NumberSet(
                    new NumberRange[]{range(1, 3), range(10, 15)},
                    new NumberRange[]{}
            );
            assertEquals("1 TO 3,10 TO 15", set.toString());
        }

        @Test
        public void toStringWithExclusions() {
            NumberSet set = new NumberSet(
                    new NumberRange[]{range(1, 10)},
                    new NumberRange[]{range(5)}
            );
            assertEquals("1 TO 10,NOT 5", set.toString());
        }
    }

    @Nested
    class NumberSetChildNodesTests {

        @Test
        public void getChildNodesIncludesAllRanges() {
            NumberSet set = new NumberSet(
                    new NumberRange[]{range(1, 3), range(5, 7)},
                    new NumberRange[]{range(10)}
            );
            var children = set.getChildNodes();
            assertEquals(3, children.size());
        }
    }
}
