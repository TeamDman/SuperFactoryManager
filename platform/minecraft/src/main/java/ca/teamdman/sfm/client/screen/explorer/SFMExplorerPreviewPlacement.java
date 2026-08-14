package ca.teamdman.sfm.client.screen.explorer;

import ca.teamdman.sfm.client.screen.text_editor.SFMTextDocumentPanelState;
import ca.teamdman.sfm.client.screen.workspace.SFMPanelReopenRecipe;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelId;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelIntentResult;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelMetadata;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspaceSide;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Places addressed documents without treating arbitrary panels as disposable.
 *
 * <p>Only an immutable text editor carrying this explorer's typed preview
 * ownership may be retired during a preview update. Terminals, writable
 * editors, and unrelated panels are never replacement candidates.</p>
 */
public final class SFMExplorerPreviewPlacement {
    public enum Mode {
        PREVIEW,
        FOCUS_PREVIEW,
        ADJACENT
    }

    public record Result(
            SFMWorkspacePanelIntentResult outcome,
            @Nullable SFMWorkspacePanelId openedPanelId,
            boolean reusedOwnedPreview
    ) {
        public boolean applied() {
            return outcome == SFMWorkspacePanelIntentResult.APPLIED;
        }
    }

    private SFMExplorerPreviewPlacement() {
    }

    /** Narrow host seam so placement policy is testable without bootstrapping Minecraft's Screen globals. */
    interface Workspace {
        boolean containsPanel(SFMWorkspacePanelId panelId);

        SFMWorkspacePanelIntentResult openToSide(
                SFMWorkspacePanelId source,
                SFMWorkspaceSide side,
                SFMScreenPanel panel,
                SFMWorkspacePanelMetadata metadata,
                @Nullable SFMPanelReopenRecipe reopenRecipe
        );

        SFMWorkspacePanelIntentResult openIntoSlot(
                SFMWorkspacePanelId slot,
                SFMScreenPanel panel,
                SFMWorkspacePanelMetadata metadata,
                @Nullable SFMPanelReopenRecipe reopenRecipe
        );

        SFMWorkspacePanelIntentResult closePanel(SFMWorkspacePanelId panelId);

        boolean focusPanel(SFMWorkspacePanelId panelId);

        SFMWorkspacePanelId focusedPanelId();

        List<SFMWorkspacePanelId> panelIds();

        @Nullable SFMWorkspacePanelMetadata panelMetadata(SFMWorkspacePanelId panelId);

        @Nullable SFMScreenPanel panelInstance(SFMWorkspacePanelId panelId);
    }

    public static Result place(
            SFMScreenMultiplexer workspace,
            SFMWorkspacePanelId explorerPanelId,
            String explorerId,
            Mode mode,
            SFMScreenPanel documentPanel,
            @Nullable SFMPanelReopenRecipe reopenRecipe
    ) {
        return place(
                new MultiplexerWorkspace(Objects.requireNonNull(workspace, "workspace")),
                explorerPanelId,
                explorerId,
                mode,
                documentPanel,
                reopenRecipe
        );
    }

    static Result place(
            Workspace workspace,
            SFMWorkspacePanelId explorerPanelId,
            String explorerId,
            Mode mode,
            SFMScreenPanel documentPanel,
            @Nullable SFMPanelReopenRecipe reopenRecipe
    ) {
        Objects.requireNonNull(workspace, "workspace");
        Objects.requireNonNull(explorerPanelId, "explorerPanelId");
        Objects.requireNonNull(explorerId, "explorerId");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(documentPanel, "documentPanel");
        if (!workspace.containsPanel(explorerPanelId)) {
            return new Result(SFMWorkspacePanelIntentResult.UNAVAILABLE, null, false);
        }

        if (mode == Mode.ADJACENT) {
            SFMWorkspacePanelIntentResult outcome = workspace.openToSide(
                    explorerPanelId,
                    SFMWorkspaceSide.RIGHT,
                    documentPanel,
                    SFMWorkspacePanelMetadata.ordinary(),
                    reopenRecipe
            );
            return new Result(
                    outcome,
                    outcome == SFMWorkspacePanelIntentResult.APPLIED
                            ? workspace.focusedPanelId()
                            : null,
                    false
            );
        }

        List<SFMWorkspacePanelId> reusable = workspace.panelIds().stream()
                .filter(id -> {
                    SFMWorkspacePanelMetadata metadata = workspace.panelMetadata(id);
                    SFMScreenPanel panel = workspace.panelInstance(id);
                    return metadata != null
                            && metadata.isExplorerPreviewOwnedBy(explorerId)
                            && panel instanceof SFMTextDocumentPanelState editor
                            && editor.isReadOnly();
                })
                .sorted(Comparator.comparingLong(SFMWorkspacePanelId::value).reversed())
                .toList();
        SFMWorkspacePanelMetadata previewMetadata = SFMWorkspacePanelMetadata.explorerPreview(explorerId);
        SFMWorkspacePanelIntentResult outcome;
        boolean reused = !reusable.isEmpty();
        if (reused) {
            outcome = workspace.openIntoSlot(
                    reusable.get(0),
                    documentPanel,
                    previewMetadata,
                    reopenRecipe
            );
        } else {
            outcome = workspace.openToSide(
                    explorerPanelId,
                    SFMWorkspaceSide.RIGHT,
                    documentPanel,
                    previewMetadata,
                    reopenRecipe
            );
        }
        if (outcome != SFMWorkspacePanelIntentResult.APPLIED) {
            return new Result(outcome, null, reused);
        }

        SFMWorkspacePanelId opened = workspace.focusedPanelId();
        // The new immutable preview is already active in the same slot before
        // the old one is retired, so the user never observes an empty slot.
        for (SFMWorkspacePanelId old : reusable) {
            if (!old.equals(opened) && workspace.containsPanel(old)) workspace.closePanel(old);
        }
        if (mode == Mode.PREVIEW) workspace.focusPanel(explorerPanelId);
        else workspace.focusPanel(opened);
        return new Result(outcome, opened, reused);
    }

    private record MultiplexerWorkspace(SFMScreenMultiplexer delegate) implements Workspace {
        private MultiplexerWorkspace {
            Objects.requireNonNull(delegate, "delegate");
        }

        @Override public boolean containsPanel(SFMWorkspacePanelId panelId) {
            return delegate.containsPanel(panelId);
        }

        @Override public SFMWorkspacePanelIntentResult openToSide(
                SFMWorkspacePanelId source,
                SFMWorkspaceSide side,
                SFMScreenPanel panel,
                SFMWorkspacePanelMetadata metadata,
                @Nullable SFMPanelReopenRecipe reopenRecipe
        ) {
            return delegate.openToSide(source, side, panel, metadata, reopenRecipe);
        }

        @Override public SFMWorkspacePanelIntentResult openIntoSlot(
                SFMWorkspacePanelId slot,
                SFMScreenPanel panel,
                SFMWorkspacePanelMetadata metadata,
                @Nullable SFMPanelReopenRecipe reopenRecipe
        ) {
            return delegate.openIntoSlot(slot, panel, metadata, reopenRecipe);
        }

        @Override public SFMWorkspacePanelIntentResult closePanel(SFMWorkspacePanelId panelId) {
            return delegate.closePanel(panelId);
        }

        @Override public boolean focusPanel(SFMWorkspacePanelId panelId) {
            return delegate.focusPanel(panelId);
        }

        @Override public SFMWorkspacePanelId focusedPanelId() {
            return delegate.focusedPanelId();
        }

        @Override public List<SFMWorkspacePanelId> panelIds() {
            return delegate.panelIds();
        }

        @Override public @Nullable SFMWorkspacePanelMetadata panelMetadata(SFMWorkspacePanelId panelId) {
            return delegate.panelMetadata(panelId);
        }

        @Override public @Nullable SFMScreenPanel panelInstance(SFMWorkspacePanelId panelId) {
            return delegate.panelInstance(panelId);
        }
    }
}
