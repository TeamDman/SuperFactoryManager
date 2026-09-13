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

class SFMExplorerRowCopySummaryActionTests {
    @Test
    void compactSummaryIncludesDisplayAndInternalRepresentationsWithoutCompleteDiagnostics() throws Exception {
        var inspection = SFMItemstackPreviewFixtures.inspection("abc.json");
        SFMActionChoice choice = SFMExplorerRowCopySummaryAction.captureChoice(inspection);
        long captureId = Long.parseLong(choice.command().substring(choice.command().lastIndexOf(' ') + 1));
        ArrayList<String> clipboard = new ArrayList<>();
        ArrayList<Component> feedback = new ArrayList<>();

        int result = new SFMExplorerRowCopySummaryAction(clipboard::add).copyCapture(captureId, feedback::add);

        assertEquals(1, result);
        assertEquals("sfm:explorer/row/summary/copy", choice.actionId().toString());
        assertEquals("Copy row summary", choice.displayText());
        assertEquals(List.of("Copied Explorer row summary"), feedback.stream().map(Component::getString).toList());
        String payload = clipboard.get(0);
        assertTrue(payload.contains("display.label: \"abc.json\""));
        assertTrue(payload.contains("display.itemstack.requested: \"minecraft:paper\""));
        assertTrue(payload.contains("internal.row-address: \"file:///C:/fixtures/abc.json\""));
        assertTrue(payload.contains("review.file-address: unavailable"));
        assertTrue(!payload.contains("relation-revision"), payload);
        assertTrue(!payload.contains("subject.metadata"), payload);
    }

    @Test
    void unknownCaptureDoesNotWriteClipboard() {
        ArrayList<String> clipboard = new ArrayList<>();
        var action = new SFMExplorerRowCopySummaryAction(clipboard::add);
        assertThrows(CommandSyntaxException.class,
                () -> action.copyCapture(Long.MAX_VALUE, ignored -> {}));
        assertTrue(clipboard.isEmpty());
    }
}
