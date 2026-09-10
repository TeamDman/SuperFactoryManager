package ca.teamdman.sfm.client.text_editor;

import ca.teamdman.sfm.client.registry.SFMTextEditors;
import ca.teamdman.sfm.client.screen.workspace.SFMPanelReopenRecipe;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanel;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.function.Supplier;

/** Typed editor recipe that retains a registry id and immutable document source. */
public record SFMTextEditorPanelRecipe(
        ResourceLocation sceneTypeId,
        ResourceLocation editorId,
        SFMTextDocumentSource documentSource,
        boolean readOnly,
        String title,
        Supplier<SFMTextDocumentSaveHandler> saveHandlerFactory
) implements SFMPanelReopenRecipe {
    public SFMTextEditorPanelRecipe {
        Objects.requireNonNull(sceneTypeId);
        Objects.requireNonNull(editorId);
        Objects.requireNonNull(documentSource);
        if (title == null || title.isBlank()) throw new IllegalArgumentException("title must not be blank");
        Objects.requireNonNull(saveHandlerFactory);
    }

    public SFMTextEditorPanelRecipe(
            ResourceLocation sceneTypeId,
            ResourceLocation editorId,
            SFMTextDocumentSource documentSource,
            boolean readOnly,
            String title
    ) {
        this(
                sceneTypeId,
                editorId,
                documentSource,
                readOnly,
                title,
                SFMTextDocumentSaveHandler::discard
        );
    }

    @Override
    public SFMScreenPanel reopen() {
        if (documentSource instanceof SFMTextDocumentSource.PathAddress
                || documentSource instanceof SFMTextDocumentSource.GeneratedReviewSurface) {
            return new ca.teamdman.sfm.client.screen.text_editor.SFMDeferredTextEditorPanel(this);
        }
        SFMTextDocumentSnapshot snapshot = documentSource
                .load(new ca.teamdman.sfm.client.explorer.lazy.SFMExplorerCancellationToken())
                .join();
        return createResolvedPanel(snapshot);
    }

    public SFMScreenPanel createResolvedPanel(SFMTextDocumentSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        ISFMTextEditorRegistration registration = SFMTextEditors.registry().get(editorId);
        if (registration == null) {
            throw new IllegalStateException("Text editor recipe references an unknown editor: " + editorId);
        }
        return registration.createPanel(new SFMTextEditorPanelOpenContext(
                editorId.toString(),
                snapshot,
                readOnly,
                title,
                Objects.requireNonNull(saveHandlerFactory.get(), "save handler factory result")
        ));
    }

    public SFMTextEditorPanelRecipe withDocumentSource(SFMTextDocumentSource source) {
        return new SFMTextEditorPanelRecipe(
                sceneTypeId,
                editorId,
                Objects.requireNonNull(source, "source"),
                readOnly,
                title,
                saveHandlerFactory
        );
    }
}
