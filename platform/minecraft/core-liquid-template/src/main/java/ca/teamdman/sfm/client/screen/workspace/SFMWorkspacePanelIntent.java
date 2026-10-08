package ca.teamdman.sfm.client.screen.workspace;

{% if features.workspace_panel_reopening %}
import org.jetbrains.annotations.Nullable;

{% endif %}
import java.util.Objects;

/** Requests a panel can make of its host without mutating Minecraft's global screen. */
public sealed interface SFMWorkspacePanelIntent {
    record Close() implements SFMWorkspacePanelIntent {
    }

{% if features.workspace_panel_metadata or features.workspace_panel_reopening %}
    record OpenToSide(
            SFMWorkspaceSide side,
            SFMScreenPanel panel,
{% if features.workspace_panel_metadata %}
{% if features.workspace_panel_reopening %}
            SFMWorkspacePanelMetadata metadata,
{% else %}
            SFMWorkspacePanelMetadata metadata
{% endif %}
{% endif %}
{% if features.workspace_panel_reopening %}
            @Nullable SFMPanelReopenRecipe reopenRecipe
{% endif %}
    ) implements SFMWorkspacePanelIntent {
{% else %}
    record OpenToSide(
            SFMWorkspaceSide side,
            SFMScreenPanel panel
    ) implements SFMWorkspacePanelIntent {
{% endif %}
        public OpenToSide {
            Objects.requireNonNull(side);
            Objects.requireNonNull(panel);
{% if features.workspace_panel_metadata %}
            Objects.requireNonNull(metadata);
{% endif %}
        }
{% if features.workspace_panel_metadata or features.workspace_panel_reopening %}

        public OpenToSide(SFMWorkspaceSide side, SFMScreenPanel panel) {
{% if features.workspace_panel_metadata %}
{% if features.workspace_panel_reopening %}
            this(side, panel, SFMWorkspacePanelMetadata.ordinary(), null);
{% else %}
            this(side, panel, SFMWorkspacePanelMetadata.ordinary());
{% endif %}
{% else %}
            this(side, panel, null);
{% endif %}
        }
{% endif %}
{% if features.workspace_panel_metadata %}
{% if features.workspace_panel_reopening %}

        public OpenToSide(
                SFMWorkspaceSide side,
                SFMScreenPanel panel,
                SFMWorkspacePanelMetadata metadata
        ) {
            this(side, panel, metadata, null);
        }
{% endif %}
{% endif %}
    }

{% if features.workspace_panel_metadata or features.workspace_panel_reopening %}
    record OpenAsTab(
            SFMScreenPanel panel,
{% if features.workspace_panel_metadata %}
{% if features.workspace_panel_reopening %}
            SFMWorkspacePanelMetadata metadata,
{% else %}
            SFMWorkspacePanelMetadata metadata
{% endif %}
{% endif %}
{% if features.workspace_panel_reopening %}
            @Nullable SFMPanelReopenRecipe reopenRecipe
{% endif %}
    ) implements SFMWorkspacePanelIntent {
{% else %}
    record OpenAsTab(
            SFMScreenPanel panel
    ) implements SFMWorkspacePanelIntent {
{% endif %}
        public OpenAsTab {
            Objects.requireNonNull(panel);
{% if features.workspace_panel_metadata %}
            Objects.requireNonNull(metadata);
{% endif %}
        }
{% if features.workspace_panel_metadata or features.workspace_panel_reopening %}

        public OpenAsTab(SFMScreenPanel panel) {
{% if features.workspace_panel_metadata %}
{% if features.workspace_panel_reopening %}
            this(panel, SFMWorkspacePanelMetadata.ordinary(), null);
{% else %}
            this(panel, SFMWorkspacePanelMetadata.ordinary());
{% endif %}
{% else %}
            this(panel, null);
{% endif %}
        }
{% endif %}
{% if features.workspace_panel_metadata %}
{% if features.workspace_panel_reopening %}

        public OpenAsTab(SFMScreenPanel panel, SFMWorkspacePanelMetadata metadata) {
            this(panel, metadata, null);
        }
{% endif %}
{% endif %}
    }
{% if features.workspace_stack_controls %}

    record Move(SFMWorkspaceSide side) implements SFMWorkspacePanelIntent {
        public Move {
            Objects.requireNonNull(side);
        }
    }

    /** Moves one existing panel entry into the pane containing another exact entry. */
    record MoveToStack(SFMWorkspacePanelId destination) implements SFMWorkspacePanelIntent {
        public MoveToStack {
            Objects.requireNonNull(destination);
        }
    }
{% endif %}
}
