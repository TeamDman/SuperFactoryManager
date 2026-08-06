package ca.teamdman.sfm.client.text_editor;

import ca.teamdman.sfm.client.registry.SFMTextEditors;
import ca.teamdman.sfm.client.screen.workspace.SFMPanelReopenRecipe;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanel;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Typed editor recipe that retains a registry id and immutable document source. */
public record SFMTextEditorPanelRecipe(
        ResourceLocation sceneTypeId,
        ResourceLocation editorId,
        SFMTextDocumentSource documentSource,
        boolean readOnly,
        String title
) implements SFMPanelReopenRecipe {
    public SFMTextEditorPanelRecipe {
        Objects.requireNonNull(sceneTypeId);
        Objects.requireNonNull(editorId);
        Objects.requireNonNull(documentSource);
        if (title == null || title.isBlank()) throw new IllegalArgumentException("title must not be blank");
    }

    @Override
    public SFMScreenPanel reopen() {
        ISFMTextEditorRegistration registration = SFMTextEditors.registry().get(editorId);
        if (registration == null) {
            throw new IllegalStateException("Text editor recipe references an unknown editor: " + editorId);
        }
        return registration.createPanel(new SFMTextEditorPanelOpenContext(
                editorId.toString(),
                documentSource.load(),
                readOnly,
                title
        ));
    }
}
