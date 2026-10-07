package ca.teamdman.sfm.client.screen.workspace;

import ca.teamdman.sfm.client.inspection.SFMItemInspectionDocument;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentLanguage;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSource;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMTextEditorScreenTypeTests {
    private static final ResourceLocation SCREEN = new ResourceLocation("sfm", "text_editor");
    private static final ResourceLocation EDITOR = new ResourceLocation("sfm", "text_editor_v3");

    @Test
    void hoveredItemTurnsTheNormalEditorOpenIntoAReadOnlyInspection() {
        var captured = new SFMItemInspectionDocument.Captured(
                "Item: Data Packet",
                "packet-value-json:\n{}\n",
                SFMTextDocumentLanguage.plainText()
        );

        var recipe = SFMTextEditorScreenType.recipeFor(SCREEN, EDITOR, Optional.of(captured));

        assertTrue(recipe.readOnly());
        assertEquals("Item: Data Packet", recipe.title());
        var source = (SFMTextDocumentSource.Literal) recipe.documentSource();
        assertEquals(captured.content(), source.text());
        assertEquals(SFMTextDocumentLanguage.plainText(), source.language());
    }

    @Test
    void noHoveredItemPreservesTheEditableBlankEditor() {
        var recipe = SFMTextEditorScreenType.recipeFor(SCREEN, EDITOR, Optional.empty());

        assertFalse(recipe.readOnly());
        assertEquals("", ((SFMTextDocumentSource.Literal) recipe.documentSource()).text());
    }
}
