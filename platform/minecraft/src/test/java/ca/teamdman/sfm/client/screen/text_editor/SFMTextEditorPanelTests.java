package ca.teamdman.sfm.client.screen.text_editor;

import ca.teamdman.sfm.client.text_editor.SFMTextEditorPanelOpenContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SFMTextEditorPanelTests {
    @Test
    void panelContextCarriesEditorIdentityAndReadOnlyState() {
        var context = new SFMTextEditorPanelOpenContext(
                "sfm:text_editor_v3", "class Example {}", true, "Review · Example.java"
        );

        assertEquals("sfm:text_editor_v3", context.editorId());
        assertEquals("class Example {}", context.initialValue());
        assertEquals("Review · Example.java", context.title());
        org.junit.jupiter.api.Assertions.assertTrue(context.readOnly());
    }

    @Test
    void panelContextRejectsMissingIdentityAndContent() {
        assertThrows(IllegalArgumentException.class,
                () -> new SFMTextEditorPanelOpenContext("", "", false, "Editor"));
        assertThrows(IllegalArgumentException.class,
                () -> new SFMTextEditorPanelOpenContext("sfm:text_editor_v3", null, false, "Editor"));
        assertThrows(IllegalArgumentException.class,
                () -> new SFMTextEditorPanelOpenContext("sfm:text_editor_v3", "", false, ""));
    }
}
