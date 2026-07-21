package ca.teamdman.sfm.client.screen.file_explorer;

import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelContext;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelIntent;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelIntentResult;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspaceSide;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

/** Constructs the explorer-first workspace and owns its single reusable preview panel. */
public final class SFMFileExplorerWorkspace {
    private SFMFileExplorerWorkspace() {
    }

    public static SFMScreenMultiplexer create(@Nullable Screen previousScreen, SFMFileExplorerSource source) {
        Controller controller = new Controller();
        SFMFileExplorerPanel explorer = new SFMFileExplorerPanel(source, controller::open);
        controller.attachExplorer(explorer);
        return SFMScreenMultiplexer.create(previousScreen, explorer);
    }

    static final class Controller {
        private SFMFileExplorerPanel explorer;
        private @Nullable SFMReadOnlyTextPanel viewer;

        void attachExplorer(SFMFileExplorerPanel explorer) { this.explorer = explorer; }

        void open(SFMFileExplorerModel.OpenIntent intent) {
            SFMFileReadResult read = explorer.model().source().readText(intent.entry().path());
            if (read.state() != SFMFileReadResult.State.READY) {
                explorer.setStatusMessage("Preview failed: " + read.message());
                return;
            }
            if (viewer != null) {
                viewer.show(intent.sourceName(), intent.entry(), read.text());
                explorer.setStatusMessage("Preview replaced: " + intent.entry().path());
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
            SFMWorkspacePanelIntentResult result = context.submit(
                    new SFMWorkspacePanelIntent.OpenToSide(SFMWorkspaceSide.RIGHT, candidate)
            );
            if (result == SFMWorkspacePanelIntentResult.APPLIED) {
                viewer = candidate;
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
