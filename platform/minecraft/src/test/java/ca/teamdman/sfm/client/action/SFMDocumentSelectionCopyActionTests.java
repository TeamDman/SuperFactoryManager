package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.context.SFMContextDocumentProjection;
import ca.teamdman.sfm.client.context.SFMContextSelectionProjection;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentRange;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSnapshot;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMDocumentSelectionCopyActionTests {
    @Test
    void copiesExactUtf8SelectionsInProjectionOrderAndAcknowledgesTheClipboard() throws Exception {
        String text = "zero 😀 one\ntwo";
        SFMContextDocumentProjection projection = projection(text,
                range(text, 5, 7), range(text, 12, 15));
        var choice = SFMDocumentSelectionCopyAction.captureChoice(projection).orElseThrow();
        long captureId = Long.parseLong(choice.command().substring(choice.command().lastIndexOf(' ') + 1));
        ArrayList<String> clipboard = new ArrayList<>();
        ArrayList<Component> feedback = new ArrayList<>();

        int result = new SFMDocumentSelectionCopyAction(clipboard::add).copyCapture(captureId, feedback::add);

        assertEquals(1, result);
        assertEquals("Copy selected text", choice.displayText());
        assertEquals(List.of("😀\ntwo"), clipboard);
        assertEquals(List.of("Copied selected text"), feedback.stream().map(Component::getString).toList());
    }

    @Test
    void collapsedSelectionIsNotOfferedAndUnknownCaptureDoesNotWriteClipboard() {
        String text = "abc";
        assertTrue(SFMDocumentSelectionCopyAction.captureChoice(projection(text, range(text, 1, 1))).isEmpty());
        ArrayList<String> clipboard = new ArrayList<>();
        assertThrows(CommandSyntaxException.class,
                () -> new SFMDocumentSelectionCopyAction(clipboard::add)
                        .copyCapture(Long.MAX_VALUE, ignored -> {}));
        assertTrue(clipboard.isEmpty());
    }

    private static SFMContextDocumentProjection projection(String text, SFMTextDocumentRange... ranges) {
        return SFMContextDocumentProjection.capture(
                "editor-test",
                SFMTextDocumentSnapshot.literal(text),
                text,
                false,
                false,
                List.of(),
                List.of(new SFMContextSelectionProjection("selection", List.of(ranges), true))
        );
    }

    private static SFMTextDocumentRange range(String text, int utf16Start, int utf16End) {
        int start = text.substring(0, utf16Start).getBytes(StandardCharsets.UTF_8).length;
        int end = text.substring(0, utf16End).getBytes(StandardCharsets.UTF_8).length;
        return new SFMTextDocumentRange(
                SFMTextDocumentRange.positionAtByteOffset(text, start),
                SFMTextDocumentRange.positionAtByteOffset(text, end)
        );
    }
}
