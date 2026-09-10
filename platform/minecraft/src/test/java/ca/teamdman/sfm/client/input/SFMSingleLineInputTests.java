package ca.teamdman.sfm.client.input;

import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class SFMSingleLineInputTests {
    private final AtomicReference<String> clipboard = new AtomicReference<>("");

    private boolean key(SFMSingleLineInput input, int key, int modifiers) {
        return input.keyPressed(key, modifiers, clipboard::get, clipboard::set);
    }

    @Test public void wordDeletionUsesCaretAndSelectionRatherThanTheEndOfTheField() {
        var input = new SFMSingleLineInput("hello world tail");
        input.select(11, 11);
        key(input, GLFW.GLFW_KEY_BACKSPACE, GLFW.GLFW_MOD_CONTROL);
        assertEquals("hello  tail", input.text());
        assertEquals(6, input.cursor());
        input.select(0, 6);
        key(input, GLFW.GLFW_KEY_BACKSPACE, GLFW.GLFW_MOD_CONTROL);
        assertEquals(" tail", input.text());
    }

    @Test public void clipboardEditsUseSelectionAndPasteIsOneEdit() {
        var input = new SFMSingleLineInput("before old after");
        input.select(7, 10);
        key(input, GLFW.GLFW_KEY_C, GLFW.GLFW_MOD_CONTROL);
        assertEquals("old", clipboard.get());
        clipboard.set("new\r\nvalue");
        key(input, GLFW.GLFW_KEY_V, GLFW.GLFW_MOD_CONTROL);
        assertEquals("before newvalue after", input.text());
        key(input, GLFW.GLFW_KEY_Z, GLFW.GLFW_MOD_CONTROL);
        assertEquals("before old after", input.text());
        key(input, GLFW.GLFW_KEY_Z, GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SHIFT);
        assertEquals("before newvalue after", input.text());
        key(input, GLFW.GLFW_KEY_A, GLFW.GLFW_MOD_CONTROL);
        key(input, GLFW.GLFW_KEY_X, GLFW.GLFW_MOD_CONTROL);
        assertEquals("before newvalue after", clipboard.get());
        assertEquals("", input.text());
    }

    @Test public void homeEndAndShiftMaintainDirectionalAnchor() {
        var input = new SFMSingleLineInput("hello world");
        key(input, GLFW.GLFW_KEY_HOME, GLFW.GLFW_MOD_SHIFT);
        assertEquals(11, input.anchor());
        assertEquals(0, input.cursor());
        key(input, GLFW.GLFW_KEY_RIGHT, 0);
        assertEquals(11, input.cursor());
        assertEquals(11, input.anchor());
        key(input, GLFW.GLFW_KEY_HOME, GLFW.GLFW_MOD_CONTROL);
        key(input, GLFW.GLFW_KEY_END, GLFW.GLFW_MOD_SHIFT);
        assertEquals("hello world", input.selectedText());
    }

    @Test public void punctuationBreaksButUnderscoresDoNot() {
        String text = "sfm:foo/bar-baz_qux";
        int cursor = text.length();
        int[] expected = {12, 11, 8, 7, 4, 3, 0};
        for (int next : expected) {
            cursor = SFMSingleLineInput.wordDestination(text, cursor, false);
            assertEquals(next, cursor);
        }
    }

    @Test public void unicodeEditsNeverLeaveHalfASupplementaryCharacter() {
        var input = new SFMSingleLineInput("A😀B");
        input.select(3, 3);
        key(input, GLFW.GLFW_KEY_BACKSPACE, 0);
        assertEquals("AB", input.text());
        assertTrue(input.charTyped('\uD83D', 0));
        assertEquals("AB", input.text());
        assertTrue(input.charTyped('\uDE00', 0));
        assertEquals("A😀B", input.text());
        input.synchronize(input.text(), 2, 2);
        assertEquals(1, input.cursor());
        var bounded = new SFMSingleLineInput("", 2, true);
        bounded.insert("A😀B");
        assertEquals("A", bounded.text());
        assertFalse(input.charTyped('x', GLFW.GLFW_MOD_CONTROL));
    }

    @Test public void externalAcknowledgmentDoesNotResetCursorOrCreateHistory() {
        var input = new SFMSingleLineInput("abc");
        input.select(1, 2);
        long generation = input.history().orElseThrow().generation();
        input.setText("abc");
        assertEquals(1, input.anchor());
        assertEquals(2, input.cursor());
        assertEquals(generation, input.history().orElseThrow().generation());
        assertFalse(key(input, GLFW.GLFW_KEY_ENTER, 0));
        assertFalse(key(input, GLFW.GLFW_KEY_TAB, 0));
    }

    @Test public void undoThenNewEditPreservesAlternativeHistory() {
        var input = new SFMSingleLineInput("");
        input.insert("old");
        String oldRevision = input.history().orElseThrow().currentRevisionId();
        key(input, GLFW.GLFW_KEY_Z, GLFW.GLFW_MOD_CONTROL);
        input.insert("new");
        assertEquals("new", input.text());
        var moved = input.history().orElseThrow().checkout(oldRevision, "test", "check-old", java.util.List.of());
        assertEquals("old", input.history().orElseThrow().currentState().text());
        assertEquals(ca.teamdman.sfm.client.history.document.SFMDocumentHistorySession.HeadMoveStatus.APPLIED, moved.status());
    }

    @Test public void hostOwnedHistoryDoesNotHandleUndoTwice() {
        var input = new SFMSingleLineInput("abc", 32, false);
        assertFalse(key(input, GLFW.GLFW_KEY_Z, GLFW.GLFW_MOD_CONTROL));
        assertFalse(key(input, GLFW.GLFW_KEY_Y, GLFW.GLFW_MOD_CONTROL));
    }

    @Test public void fieldValidationRejectsEditsWithoutChangingSelectionOrHistory() {
        var input = new SFMSingleLineInput("12", 3, true, value -> value.matches("[0-9]{1,3}"));
        input.selectAll();
        long generation = input.history().orElseThrow().generation();
        input.charTyped('x', 0);
        key(input, GLFW.GLFW_KEY_BACKSPACE, 0);
        assertEquals("12", input.text());
        assertEquals("12", input.selectedText());
        assertEquals(generation, input.history().orElseThrow().generation());
        input.insert("7");
        assertEquals("7", input.text());
        key(input, GLFW.GLFW_KEY_Z, GLFW.GLFW_MOD_CONTROL);
        assertEquals("12", input.text());
        input.select(0, 0);
        key(input, GLFW.GLFW_KEY_DELETE, GLFW.GLFW_MOD_CONTROL);
        assertEquals(0, input.anchor());
        assertEquals("12", input.text());
    }
}
