package ca.teamdman.sfm.client.screen.history.chamber;

import ca.teamdman.sfm.client.history.chamber.SFMChamberDocumentState;
import ca.teamdman.sfm.client.history.chamber.SFMDecimalNumberingChamber;
import ca.teamdman.sfm.client.history.chamber.SFMDecimalNumberingTrajectoryController;
import ca.teamdman.sfm.client.registry.SFMKeyboardUsageSituations;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentRange;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class SFMDecimalNumberingChamberPanelTests {
    @Test
    void panelPublishesTemporalDocumentAndEpisodeCapabilities() throws Exception {
        SFMDecimalNumberingTrajectoryController firstController = controller("episode-a");
        SFMDecimalNumberingTrajectoryController secondController = controller("episode-b");
        SFMDecimalNumberingChamberPanel first = panelWithoutMinecraftBootstrap(firstController);
        SFMDecimalNumberingChamberPanel second = panelWithoutMinecraftBootstrap(secondController);

        assertEquals(SFMKeyboardUsageSituations.TEMPORAL_DOCUMENT, first.keyboardUsageSituationId());
        assertEquals(firstController.machineId(), first.episodeId().orElseThrow());
        assertFalse(first.isReadOnly());
        assertNotSame(first, second);
        assertNotSame(first.controller(), second.controller());
    }

    @Test
    void semanticSelectionWitnessProjectsToExactUnicodeTextRanges() {
        String text = "- 😀 apples\n\t- café\n";
        SFMChamberDocumentState root = SFMChamberDocumentState.root(text);
        SFMDecimalNumberingChamber chamber = new SFMDecimalNumberingChamber();
        SFMChamberDocumentState selected = chamber.transitions(root, chamber.requireTarget(root)).stream()
                .filter(transition -> transition.action()
                        instanceof SFMDecimalNumberingChamber.SelectAllHyphenMarkersAction)
                .findFirst()
                .orElseThrow()
                .result();

        List<SFMTextDocumentRange> ranges = SFMDecimalNumberingChamberPanel.selectionRanges(selected);

        assertEquals(2, ranges.size());
        assertEquals("-", sliceUtf8(text, ranges.get(0)));
        assertEquals("-", sliceUtf8(text, ranges.get(1)));
        assertEquals(0, ranges.get(0).start().line());
        assertEquals(0, ranges.get(0).start().column());
        assertEquals(1, ranges.get(1).start().line());
        assertEquals(1, ranges.get(1).start().column());
    }

    @Test
    void editorProjectionPreservesTheAuthoritativeUnrenderedTrailingLineEnding() {
        assertEquals(
                "- apples\n- apricots\n- bananas\n",
                SFMDecimalNumberingChamberPanel.restoreAuthoritativeTrailingLineEnding(
                        "- apples\n- bananas\n",
                        "- apples\n- apricots\n- bananas"
                )
        );
        assertEquals(
                "first\r\nsecond\r\n",
                SFMDecimalNumberingChamberPanel.restoreAuthoritativeTrailingLineEnding(
                        "first\r\nsecond\r\n",
                        "first\r\nsecond"
                )
        );
        assertEquals(
                "no-ending",
                SFMDecimalNumberingChamberPanel.restoreAuthoritativeTrailingLineEnding(
                        "no-ending",
                        "no-ending"
                )
        );
    }

    private static SFMDecimalNumberingTrajectoryController controller(String episodeId) {
        return new SFMDecimalNumberingTrajectoryController(
                episodeId,
                "document-1",
                SFMDecimalNumberingTrajectoryController.INITIAL_TEXT,
                () -> "sha256:unchanged"
        );
    }

    private static SFMDecimalNumberingChamberPanel panelWithoutMinecraftBootstrap(
            SFMDecimalNumberingTrajectoryController controller
    ) throws Exception {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        SFMDecimalNumberingChamberPanel panel = (SFMDecimalNumberingChamberPanel)
                ((Unsafe) unsafeField.get(null)).allocateInstance(SFMDecimalNumberingChamberPanel.class);
        Field controllerField = SFMDecimalNumberingChamberPanel.class.getDeclaredField("controller");
        controllerField.setAccessible(true);
        controllerField.set(panel, controller);
        return panel;
    }

    private static String sliceUtf8(String text, SFMTextDocumentRange range) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        return new String(
                bytes,
                range.start().byteOffset(),
                range.end().byteOffset() - range.start().byteOffset(),
                StandardCharsets.UTF_8
        );
    }
}
