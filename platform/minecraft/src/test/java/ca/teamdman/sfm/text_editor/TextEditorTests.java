package ca.teamdman.sfm.text_editor;
//
//import ca.teamdman.sfm.client.text_editor.TextEditContext;
//import ca.teamdman.sfm.client.text_editor.action.DeleteSelectionOrCharacterToTheLeftForEachCursorAction;
//import ca.teamdman.sfm.client.text_editor.action.KeyboardImpulse;
//import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
//import org.antlr.v4.runtime.misc.IntervalSet;
//import org.junit.jupiter.api.Test;
//import org.lwjgl.glfw.GLFW;
//
//import static org.junit.jupiter.api.Assertions.assertEquals;

public class TextEditorTests {
//    @Test
//    public void insertText() {
//        TextEditContext context = new TextEditContext();
//        assertEquals(1, context.lines().size(), "Should have one line before insertion");
//        context.insertTextAtCursors("Ahoy, world!");
//        assertEquals(1, context.lines().size(), "Should have one line after insertion");
//        assertEquals("Ahoy, world!", context.lines().get(0).toString(), "Inserted text should match");
//    }
//
//    @Test
//    public void insertTextTwoCursors() {
//        TextEditContext context = new TextEditContext();
//        context.multiCursor().addCursor(0, 0, 0, 0);
//        assertEquals(1, context.lines().size(), "Should have one line before insertion");
//        context.insertTextAtCursors("Ahoy, world!");
//        assertEquals(1, context.lines().size(), "Should have one line after insertion");
//        assertEquals("Ahoy, world!Ahoy, world!", context.lines().get(0).toString(), "Inserted text should match");
//    }
//
//    @Test
//    public void deleteSelection() {
//        TextEditContext context = new TextEditContext();
//        String text = "Hello, world!";
//        context.insertTextAtCursors(text);
//        assertEquals(1, context.lines().size(), "Should have one line before deletion");
//        assertEquals(text, context.lines().get(0).toString(), "Initial text should match");
//
//        // Set the cursor to select "Hello"
//        context.multiCursor().cursors().clear();
//        context.multiCursor().addCursor(0, 1, 0, 1 + "ello".length());
//
//        context.deleteSelectedText();
//        assertEquals(1, context.lines().size(), "Should have one line after deletion");
//        assertEquals("H, world!", context.lines().get(0).toString(), "Text after deletion should match");
//    }
//
//    @Test
//    public void deleteSelectionTwoCursors() {
//        TextEditContext context = new TextEditContext();
//        String text = "Hello, world!";
//        context.insertTextAtCursors(text);
//        context.multiCursor().cursors().clear();
//        context.multiCursor().addCursor(0, 0, 0, "Hello".length());
//        context.multiCursor().addCursor(0, "Hello, ".length(), 0, "Hello, world".length());
//        context.deleteSelectedText();
//        assertEquals(1, context.lines().size(), "Should have one line after deletion");
//        assertEquals(", !", context.lines().get(0).toString(), "Text after deletion should match");
//    }
//
//    @Test
//    public void backspace() {
//        TextEditContext context = new TextEditContext();
//        String text = "Hello, world!";
//        context.insertTextAtCursors(text);
//        context.multiCursor().cursors().clear();
//        context.multiCursor().addCursor(0, text.length(), 0, text.length());
//        new DeleteSelectionOrCharacterToTheLeftForEachCursorAction()
//                .apply(context, new KeyboardImpulse(GLFW.GLFW_KEY_BACKSLASH, 0, 0));
//        assertEquals(1, context.lines().size(), "Should have one line after backspace");
//        assertEquals("Hello, world", context.lines().get(0).toString(), "Text after backspace should match");
//    }
//
//    @Test
//    public void selectedCharactersByLineTest() {
//        String text = "abc\ndef\nghi\njkl";
//        TextEditContext context = new TextEditContext(text);
//        context.multiCursor().cursors().clear();
//        context.multiCursor().addCursor(0,0,3,3);
//        Int2ObjectOpenHashMap<IntervalSet> selectedCharactersByLine = context.selectedCharactersByLine();
//        assertEquals(4, selectedCharactersByLine.size(), "Should have 4 lines in selected characters map");
//        assertEquals(IntervalSet.of(0, 2), selectedCharactersByLine.get(0), "Line 0 should have all characters selected");
//        assertEquals(IntervalSet.of(0, 2), selectedCharactersByLine.get(1), "Line 1 should have all characters selected");
//        assertEquals(IntervalSet.of(0, 2), selectedCharactersByLine.get(2), "Line 2 should have all characters selected");
//        assertEquals(IntervalSet.of(0, 2), selectedCharactersByLine.get(3), "Line 3 should have all characters selected");
//    }
}
