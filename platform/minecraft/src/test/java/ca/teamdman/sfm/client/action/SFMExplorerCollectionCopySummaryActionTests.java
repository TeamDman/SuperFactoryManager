package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.screen.SFMActionChoice;
import ca.teamdman.sfm.client.theme.preview.SFMItemstackPreviewFixtures;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMExplorerCollectionCopySummaryActionTests {
    @Test
    void childrenSummaryCapturesImmediatePublishedRowsAndCompletenessEvidence() throws Exception {
        var anchor = SFMItemstackPreviewFixtures.inspection("parent").row();
        var entries = List.of(
                SFMItemstackPreviewFixtures.inspection("alpha.json"),
                SFMItemstackPreviewFixtures.inspection("beta.md")
        );
        SFMActionChoice choice = SFMExplorerCollectionCopySummaryAction.captureChoice(
                SFMExplorerCollectionCopySummaryAction.Kind.CHILDREN, anchor, entries, 2);
        long captureId = captureId(choice);
        ArrayList<String> clipboard = new ArrayList<>();
        ArrayList<Component> feedback = new ArrayList<>();

        int result = new SFMExplorerCollectionCopySummaryAction(
                SFMExplorerCollectionCopySummaryAction.Kind.CHILDREN, clipboard::add)
                .copyCapture(captureId, feedback::add);

        assertEquals(1, result);
        assertEquals("sfm:explorer/row/children/summary/copy", choice.actionId().toString());
        assertEquals("Copy children summary", choice.displayText());
        assertEquals(List.of("Copied Explorer children summary"),
                feedback.stream().map(Component::getString).toList());
        String payload = clipboard.get(0);
        assertTrue(payload.startsWith("schema: sfm.explorer-children-summary/1\n"));
        assertTrue(payload.contains("children.scope: immediate-published"));
        assertTrue(payload.contains("children.page-completeness: unavailable"));
        assertTrue(payload.contains("scope.total-entry-count: 2"));
        assertTrue(payload.contains("entry[0].display.label: \"alpha.json\""));
        assertTrue(payload.contains("entry[1].display.label: \"beta.md\""));
        assertTrue(payload.contains("entry[0].internal.row-address: \"file:///C:/fixtures/abc.json\""));
        assertTrue(!payload.contains("subject.metadata"), payload);
    }

    @Test
    void selectionSummaryRetainsCountAndCompactEntryIdentities() throws Exception {
        var anchor = SFMItemstackPreviewFixtures.inspection("clicked.java").row();
        var entries = List.of(
                SFMItemstackPreviewFixtures.inspection("first.java"),
                SFMItemstackPreviewFixtures.inspection("second.rs")
        );
        SFMActionChoice choice = SFMExplorerCollectionCopySummaryAction.captureChoice(
                SFMExplorerCollectionCopySummaryAction.Kind.SELECTION, anchor, entries, 2);
        long captureId = captureId(choice);
        ArrayList<String> clipboard = new ArrayList<>();

        new SFMExplorerCollectionCopySummaryAction(
                SFMExplorerCollectionCopySummaryAction.Kind.SELECTION, clipboard::add)
                .copyCapture(captureId, ignored -> {});

        assertEquals("sfm:explorer/selection/summary/copy", choice.actionId().toString());
        assertEquals("Copy selected entries summary", choice.displayText());
        String payload = clipboard.get(0);
        assertTrue(payload.startsWith("schema: sfm.explorer-selection-summary/1\n"));
        assertTrue(payload.contains("selection.id: unavailable"));
        assertTrue(payload.contains("scope.captured-entry-count: 2"));
        assertTrue(payload.contains("scope.entries-truncated: false"));
        assertTrue(payload.contains("entry[1].display.label: \"second.rs\""));
    }

    @Test
    void unknownCollectionCaptureDoesNotWriteClipboard() {
        ArrayList<String> clipboard = new ArrayList<>();
        var action = new SFMExplorerCollectionCopySummaryAction(
                SFMExplorerCollectionCopySummaryAction.Kind.SELECTION, clipboard::add);
        assertThrows(CommandSyntaxException.class,
                () -> action.copyCapture(Long.MAX_VALUE, ignored -> {}));
        assertTrue(clipboard.isEmpty());
    }

    private static long captureId(SFMActionChoice choice) {
        return Long.parseLong(choice.command().substring(choice.command().lastIndexOf(' ') + 1));
    }
}
