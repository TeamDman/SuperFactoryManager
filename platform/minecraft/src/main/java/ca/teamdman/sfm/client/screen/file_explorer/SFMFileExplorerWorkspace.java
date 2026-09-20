package ca.teamdman.sfm.client.screen.file_explorer;

import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelContext;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelIntent;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelIntentResult;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelMetadata;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelId;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspaceSide;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

/** Constructs the explorer-first workspace and owns its explorer-scoped preview slot. */
public final class SFMFileExplorerWorkspace {
    private SFMFileExplorerWorkspace() {
    }

    public static SFMScreenMultiplexer create(@Nullable Screen previousScreen, SFMFileExplorerSource source) {
        SFMFileExplorerPanel explorer = createPanel(source, true);
        return SFMFileExplorerPanelRecipe.from(source)
                .map(recipe -> SFMScreenMultiplexer.create(previousScreen, explorer, recipe))
                .orElseGet(() -> SFMScreenMultiplexer.create(previousScreen, explorer));
    }

    public static SFMScreenMultiplexer create(
            @Nullable Screen previousScreen,
            SFMFileExplorerSource source,
            SFMFilePresentationRegistry presentations
    ) {
        return SFMScreenMultiplexer.create(previousScreen, createPanel(source, presentations, false));
    }

    static SFMFileExplorerPanel createPanel(SFMFileExplorerSource source, boolean recipeBacked) {
        return createPanel(source, SFMFilePresentationRegistry.createDefault(), recipeBacked);
    }

    private static SFMFileExplorerPanel createPanel(
            SFMFileExplorerSource source,
            SFMFilePresentationRegistry presentations,
            boolean recipeBacked
    ) {
        Controller controller = new Controller();
        SFMFileExplorerPanel explorer = new SFMFileExplorerPanel(
                source,
                controller::open,
                presentations,
                recipeBacked);
        controller.attachExplorer(explorer);
        return explorer;
    }

    static final class Controller {
        private SFMFileExplorerPanel explorer;
        private @Nullable SFMReadOnlyTextPanel viewer;
        private @Nullable SFMWorkspacePanelId previewSlot;

        void attachExplorer(SFMFileExplorerPanel explorer) { this.explorer = explorer; }

        void open(SFMFileExplorerModel.OpenIntent intent) {
            SFMFileReadResult read = explorer.model().source().readText(intent.entry().path());
            if (read.state() != SFMFileReadResult.State.READY) {
                explorer.setStatusMessage("Preview failed: " + read.message());
                return;
            }
            SFMWorkspacePanelContext context = explorer.hostContext();
            if (context == null) {
                explorer.setStatusMessage("Preview unavailable: explorer is not hosted");
                return;
            }
            SFMReadOnlyTextPanel candidate = new SFMReadOnlyTextPanel(() -> {
                if (viewer == candidateReference) viewer = null;
            }, explorer::onFilesDrop);
            candidateReference = candidate;
            candidate.show(intent.sourceName(), intent.entry(), read.text());
            SFMWorkspacePanelIntentResult result;
            @Nullable SFMWorkspacePanelId openedEntry = null;
            SFMWorkspacePanelMetadata metadata = SFMWorkspacePanelMetadata.explorerPreview(
                    context.panelId().toString());
            if (context.host() instanceof SFMScreenMultiplexer workspace) {
                if (previewSlot != null && !workspace.containsPanel(previewSlot)) previewSlot = null;
                if (previewSlot != null) {
                    result = workspace.openIntoSlot(previewSlot, candidate, metadata);
                    if (result == SFMWorkspacePanelIntentResult.APPLIED) openedEntry = workspace.focusedPanelId();
                } else {
                    result = context.submit(new SFMWorkspacePanelIntent.OpenToSide(
                            SFMWorkspaceSide.RIGHT, candidate, metadata));
                    if (result == SFMWorkspacePanelIntentResult.APPLIED) {
                        previewSlot = workspace.focusedPanelId();
                        openedEntry = previewSlot;
                    }
                }
            } else if (viewer != null) {
                viewer.show(intent.sourceName(), intent.entry(), read.text());
                explorer.setStatusMessage("Preview replaced: " + intent.entry().path());
                return;
            } else {
                result = context.submit(new SFMWorkspacePanelIntent.OpenToSide(
                        SFMWorkspaceSide.RIGHT, candidate, metadata));
            }
            if (result == SFMWorkspacePanelIntentResult.APPLIED) {
                viewer = candidate;
                if (context.host() instanceof SFMScreenMultiplexer workspace) {
                    if (intent.focusPreview() && openedEntry != null) workspace.focusPanel(openedEntry);
                    else workspace.focusPanel(context.panelId());
                }
                explorer.setStatusMessage("Preview opened: " + intent.entry().path());
            } else {
                candidateReference = null;
                explorer.setStatusMessage("Preview unavailable: host returned " + result);
            }
        }

        private @Nullable SFMReadOnlyTextPanel candidateReference;

        @Nullable SFMReadOnlyTextPanel viewer() { return viewer; }
    }
}
