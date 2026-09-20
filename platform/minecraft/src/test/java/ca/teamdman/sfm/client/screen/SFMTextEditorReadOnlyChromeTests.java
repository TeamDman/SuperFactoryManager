package ca.teamdman.sfm.client.screen;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SFMTextEditorReadOnlyChromeTests {
    private static final SFMTextEditorReadOnlyChrome.Rect CONFIG =
            new SFMTextEditorReadOnlyChrome.Rect(4, 76, 16, 20);

    @Test
    void localizedStatusUsesTheConciseRequestedWords() {
        assertEquals("Read-only", SFMDrawCanvasScreen.TEXT_EDITOR_V3_READ_ONLY_DOCUMENT.getStub());
    }

    @Test
    void writableDocumentsOmitTheChrome() {
        assertEquals(
                Optional.empty(),
                SFMTextEditorReadOnlyChrome.layout(
                        false,
                        CONFIG,
                        new SFMTextEditorReadOnlyChrome.Rect(339, 76, 80, 20),
                        48,
                        9
                )
        );
    }

    @Test
    void ordinaryLayoutCentresContainedChromeBetweenUnchangedControls() {
        SFMTextEditorReadOnlyChrome.Rect done = new SFMTextEditorReadOnlyChrome.Rect(339, 76, 80, 20);
        SFMTextEditorReadOnlyChrome.Rect originalConfig = CONFIG;
        SFMTextEditorReadOnlyChrome.Rect originalDone = done;

        SFMTextEditorReadOnlyChrome.Layout layout = SFMTextEditorReadOnlyChrome.layout(
                true,
                CONFIG,
                done,
                48,
                9
        ).orElseThrow();

        assertEquals(originalConfig, CONFIG, "layout must not alter the # hit bounds");
        assertEquals(originalDone, done, "layout must not alter the Done hit bounds");
        assertFalse(layout.background().overlaps(CONFIG));
        assertFalse(layout.background().overlaps(done));
        assertTrue(layout.background().contains(layout.textArea()));
        assertEquals(
                (CONFIG.right() + done.x()) / 2,
                layout.background().x() + layout.background().width() / 2
        );
    }

    @Test
    void narrowLayoutTrimsToTheFreeLaneWithoutOverlappingButtons() {
        SFMTextEditorReadOnlyChrome.Rect done = new SFMTextEditorReadOnlyChrome.Rect(52, 76, 80, 20);

        SFMTextEditorReadOnlyChrome.Layout layout = SFMTextEditorReadOnlyChrome.layout(
                true,
                CONFIG,
                done,
                80,
                9
        ).orElseThrow();

        assertTrue(layout.background().width() < 80, "narrow chrome must be width-clamped");
        assertTrue(layout.background().x() >= CONFIG.right());
        assertTrue(layout.background().right() <= done.x());
        assertFalse(layout.background().overlaps(CONFIG));
        assertFalse(layout.background().overlaps(done));
        assertTrue(layout.background().contains(layout.textArea()));
    }

    @Test
    void anOverlappedControlLaneOmitsChromeInsteadOfInterceptingEitherNeighbour() {
        assertTrue(SFMTextEditorReadOnlyChrome.layout(
                true,
                CONFIG,
                new SFMTextEditorReadOnlyChrome.Rect(18, 76, 80, 20),
                48,
                9
        ).isEmpty());
    }
}
