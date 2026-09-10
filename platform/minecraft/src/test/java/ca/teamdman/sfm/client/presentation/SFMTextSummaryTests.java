package ca.teamdman.sfm.client.presentation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SFMTextSummaryTests {
    @Test
    void compactionIsPresentationOnlyAndNeverSplitsSupplementaryCharacters() {
        String original = "  café\r\n雪\t😀\u2028next  ";
        assertEquals("café 雪 😀 next", SFMTextSummary.singleLine(original));
        assertEquals("😀😀…", SFMTextSummary.codePoints("😀😀😀😀", 3));
        assertEquals("  café\r\n雪\t😀\u2028next  ", original);
    }

    @Test
    void pixelBudgetHandlesZeroNarrowAndVariableWidthText() {
        java.util.function.ToIntFunction<String> measure = value -> value.codePoints()
                .map(codePoint -> codePoint == 'W' ? 8 : codePoint == 'i' ? 2 : 4).sum();
        for (int width = 0; width < 90; width++) {
            String result = SFMTextSummary.fitLine("WW😀 iii\n尾", width, measure);
            assertTrue(measure.applyAsInt(result) <= width);
            assertFalse(result.contains("\n"));
            assertFalse(result.codePoints().anyMatch(cp -> cp >= 0xD800 && cp <= 0xDFFF));
        }
        assertEquals("", SFMTextSummary.fitLine("Wide", 2, measure));
        assertEquals("a b", SFMTextSummary.fitLine("a\nb", 100, measure));
    }
}
