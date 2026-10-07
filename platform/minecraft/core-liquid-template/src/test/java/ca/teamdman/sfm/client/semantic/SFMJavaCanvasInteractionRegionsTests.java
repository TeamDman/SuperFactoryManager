package ca.teamdman.sfm.client.semantic;

import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMJavaCanvasInteractionRegionsTests {
    @Test
    void everyNonWhitespaceScalarHasExactlyOneExplicitRegion() {
        String source = """
                @Mod(value = \"sfm\")
                public final class A<T> { // comment
                    int n = 0xFF + 1_000;
                    String wide = \"λ😀\";
                    void run() { this.n++; }
                }
                /* malformed on purpose""";
        var index = SFMJavaCanvasInteractionRegions.index(source);
        HashSet<Integer> witnessed = new HashSet<>();
        for (var region : index.regions()) {
            for (int offset = region.utf16Start(); offset < region.utf16End();) {
                int codePoint = source.codePointAt(offset);
                if (!Character.isWhitespace(codePoint)) {
                    assertTrue(witnessed.add(offset), "overlap at UTF-16 offset " + offset);
                    assertEquals(region, index.atUtf16(offset).orElseThrow());
                }
                offset += Character.charCount(codePoint);
            }
        }
        for (int offset = 0; offset < source.length();) {
            int codePoint = source.codePointAt(offset);
            if (!Character.isWhitespace(codePoint)) {
                assertTrue(witnessed.contains(offset), "unclassified UTF-16 offset " + offset);
            }
            offset += Character.charCount(codePoint);
        }
    }

    @Test
    void annotationMarkerNavigatesThroughItsFollowingIdentifier() {
        String source = "@Mod class A {}";
        var marker = SFMJavaCanvasInteractionRegions.index(source).atUtf16(0).orElseThrow();
        assertEquals(SFMJavaCanvasInteractionRegions.Kind.ANNOTATION_MARKER, marker.kind());
        assertEquals(1, marker.navigationUtf16Offset());
    }

    @Test
    void delimitersPunctuationCommentsAndMalformedLiteralsRemainDeliberateRegions() {
        String source = "void f(){ call(a, b); } // hi\nString x = \"unterminated";
        var index = SFMJavaCanvasInteractionRegions.index(source);
        assertEquals(SFMJavaCanvasInteractionRegions.Kind.DELIMITER,
                index.atUtf16(source.indexOf('{')).orElseThrow().kind());
        assertEquals(SFMJavaCanvasInteractionRegions.Kind.SEPARATOR,
                index.atUtf16(source.indexOf(';')).orElseThrow().kind());
        assertEquals(SFMJavaCanvasInteractionRegions.Kind.LINE_COMMENT,
                index.atUtf16(source.indexOf("//") + 1).orElseThrow().kind());
        assertEquals(SFMJavaCanvasInteractionRegions.Kind.STRING_LITERAL,
                index.atUtf16(source.lastIndexOf('"')).orElseThrow().kind());
    }
}
