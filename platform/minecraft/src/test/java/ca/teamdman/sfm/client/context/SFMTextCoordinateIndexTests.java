package ca.teamdman.sfm.client.context;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SFMTextCoordinateIndexTests {
    @Test void exactIndexAgreesWithReferenceAtEveryScalarAndCrLfBoundary() {
        String text = "α😀\r\n\n\rx\r\n";
        var index = new SFMTextCoordinateIndex(text);
        for (int offset = 0; offset <= text.length(); offset++) {
            int requested = offset;
            try {
                var expected = SFMContextTextCoordinates.atUtf16Offset(text, offset);
                assertEquals(expected, index.atUtf16(offset));
                assertEquals(offset, index.utf16(expected));
            } catch (IllegalArgumentException invalid) {
                assertThrows(IllegalArgumentException.class, () -> index.atUtf16(requested));
            }
        }
        assertThrows(IllegalArgumentException.class, () -> index.atUtf16(text.length() + 1));
        assertThrows(IllegalArgumentException.class, () -> index.utf16(
                new ca.teamdman.sfm.client.text_editor.SFMTextDocumentPosition(4, 9, 2)));
    }
}
