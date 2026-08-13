package ca.teamdman.sfm.client.screen.text_editor;

import ca.teamdman.sfm.client.text_editor.SFMTextEditorPanelOpenContext;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSaveResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;

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

    @Test
    void panelContextCarriesTypedSaveSuccessAndRejection() {
        var accepted = new SFMTextEditorPanelOpenContext(
                "sfm:text_editor_v3",
                "before",
                false,
                "Editor",
                content -> content.equals("after")
                        ? SFMTextDocumentSaveResult.success()
                        : SFMTextDocumentSaveResult.rejected(
                                net.minecraft.network.chat.Component.literal("stale")
                        )
        );

        assertTrue(accepted.saveHandler().save("after").saved());
        assertFalse(accepted.saveHandler().save("other").saved());
        assertEquals("stale", accepted.saveHandler().save("other")
                .diagnostic().orElseThrow().getString());
    }

    @Test
    void panelSaveClosesOnlyAfterSuccessAndReturnsRejectionDiagnostic() {
        AtomicBoolean closed = new AtomicBoolean();
        var panelContext = new SFMTextEditorPanelOpenContext(
                "sfm:v1",
                "before",
                false,
                "Explorer Location",
                content -> content.equals("accepted")
                        ? SFMTextDocumentSaveResult.success()
                        : SFMTextDocumentSaveResult.rejected(
                                net.minecraft.network.chat.Component.literal("stale revision")
                        )
        );
        var screenContext = SFMTextEditorPanel.screenContext(panelContext, () -> closed.set(true));

        SFMTextDocumentSaveResult rejected = screenContext.trySaveAndClose("rejected");
        assertFalse(rejected.saved());
        assertEquals("stale revision", rejected.diagnostic().orElseThrow().getString());
        assertFalse(closed.get());

        assertTrue(screenContext.trySaveAndClose("accepted").saved());
        assertTrue(closed.get());
    }
}
