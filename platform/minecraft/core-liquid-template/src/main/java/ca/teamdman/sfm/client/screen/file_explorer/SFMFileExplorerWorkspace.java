package ca.teamdman.sfm.client.screen.file_explorer;

import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelContext;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelIntent;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelIntentResult;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.workspace_stack_controls and features.workspace_panel_actions and features.workspace_panel_metadata and features.workspace_panel_lookup %}
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelMetadata;
{% endif %}
{% if features.workspace_stack_controls and features.workspace_panel_actions and features.workspace_panel_metadata and features.workspace_panel_lookup %}
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelId;
{% endif %}
{% else %}
{% endcase %}
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspaceSide;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
/** Constructs the explorer-first workspace and owns its explorer-scoped preview slot. */
{% else %}
/** Constructs the explorer-first workspace and owns its single reusable preview panel. */
{% endcase %}
public final class SFMFileExplorerWorkspace {
    private SFMFileExplorerWorkspace() {
    }

    public static SFMScreenMultiplexer create(@Nullable Screen previousScreen, SFMFileExplorerSource source) {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.workspace_panel_reopening %}
        SFMFileExplorerPanel explorer = createPanel(source, true);
        return SFMFileExplorerPanelRecipe.from(source)
                .map(recipe -> SFMScreenMultiplexer.create(previousScreen, explorer, recipe))
                .orElseGet(() -> SFMScreenMultiplexer.create(previousScreen, explorer));
{% else %}
        return SFMScreenMultiplexer.create(previousScreen, createPanel(source, false));
{% endif %}
{% else %}
        return create(previousScreen, source, SFMFilePresentationRegistry.createDefault());
{% endcase %}
    }

    public static SFMScreenMultiplexer create(
            @Nullable Screen previousScreen,
            SFMFileExplorerSource source,
            SFMFilePresentationRegistry presentations
    ) {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
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
{% else %}
{% endcase %}
        Controller controller = new Controller();
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        SFMFileExplorerPanel explorer = new SFMFileExplorerPanel(
                source,
                controller::open,
                presentations,
                recipeBacked);
{% else %}
        SFMFileExplorerPanel explorer = new SFMFileExplorerPanel(source, controller::open, presentations);
{% endcase %}
        controller.attachExplorer(explorer);
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        return explorer;
{% else %}
        return SFMScreenMultiplexer.create(previousScreen, explorer);
{% endcase %}
    }

    static final class Controller {
        private SFMFileExplorerPanel explorer;
        private @Nullable SFMReadOnlyTextPanel viewer;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.workspace_stack_controls and features.workspace_panel_actions and features.workspace_panel_metadata and features.workspace_panel_lookup %}
        private @Nullable SFMWorkspacePanelId previewSlot;
{% endif %}
{% else %}
{% endcase %}

        void attachExplorer(SFMFileExplorerPanel explorer) { this.explorer = explorer; }

        void open(SFMFileExplorerModel.OpenIntent intent) {
            SFMFileReadResult read = explorer.model().source().readText(intent.entry().path());
            if (read.state() != SFMFileReadResult.State.READY) {
                explorer.setStatusMessage("Preview failed: " + read.message());
                return;
            }
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.workspace_stack_controls and features.workspace_panel_actions and features.workspace_panel_metadata and features.workspace_panel_lookup %}
{% else %}
            if (viewer != null) {
                viewer.show(intent.sourceName(), intent.entry(), read.text());
                explorer.setStatusMessage("Preview replaced: " + intent.entry().path());
                return;
            }
{% endif %}
{% else %}
            if (viewer != null) {
                viewer.show(intent.sourceName(), intent.entry(), read.text());
                explorer.setStatusMessage("Preview replaced: " + intent.entry().path());
                return;
            }
{% endcase %}
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
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.workspace_stack_controls and features.workspace_panel_actions and features.workspace_panel_metadata and features.workspace_panel_lookup %}
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
{% else %}
            SFMWorkspacePanelIntentResult result = context.submit(
                    new SFMWorkspacePanelIntent.OpenToSide(SFMWorkspaceSide.RIGHT, candidate)
            );
{% endif %}
{% else %}
            SFMWorkspacePanelIntentResult result = context.submit(
                    new SFMWorkspacePanelIntent.OpenToSide(SFMWorkspaceSide.RIGHT, candidate)
            );
{% endcase %}
            if (result == SFMWorkspacePanelIntentResult.APPLIED) {
                viewer = candidate;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.workspace_stack_controls and features.workspace_panel_actions and features.workspace_panel_metadata and features.workspace_panel_lookup %}
                if (context.host() instanceof SFMScreenMultiplexer workspace) {
                    if (intent.focusPreview() && openedEntry != null) workspace.focusPanel(openedEntry);
                    else workspace.focusPanel(context.panelId());
                }
{% endif %}
{% else %}
{% endcase %}
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
