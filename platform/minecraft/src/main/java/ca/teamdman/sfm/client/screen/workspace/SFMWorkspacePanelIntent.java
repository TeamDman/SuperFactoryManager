package ca.teamdman.sfm.client.screen.workspace;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** Requests a panel can make of its host without mutating Minecraft's global screen. */
public sealed interface SFMWorkspacePanelIntent {
    record Close() implements SFMWorkspacePanelIntent {
    }

    record OpenToSide(
            SFMWorkspaceSide side,
            SFMScreenPanel panel,
            SFMWorkspacePanelMetadata metadata,
            @Nullable SFMPanelReopenRecipe reopenRecipe
    ) implements SFMWorkspacePanelIntent {
        public OpenToSide {
            Objects.requireNonNull(side);
            Objects.requireNonNull(panel);
            Objects.requireNonNull(metadata);
        }

        public OpenToSide(SFMWorkspaceSide side, SFMScreenPanel panel) {
            this(side, panel, SFMWorkspacePanelMetadata.ordinary(), null);
        }

        public OpenToSide(
                SFMWorkspaceSide side,
                SFMScreenPanel panel,
                SFMWorkspacePanelMetadata metadata
        ) {
            this(side, panel, metadata, null);
        }
    }

    record OpenAsTab(
            SFMScreenPanel panel,
            SFMWorkspacePanelMetadata metadata,
            @Nullable SFMPanelReopenRecipe reopenRecipe
    ) implements SFMWorkspacePanelIntent {
        public OpenAsTab {
            Objects.requireNonNull(panel);
            Objects.requireNonNull(metadata);
        }

        public OpenAsTab(SFMScreenPanel panel) {
            this(panel, SFMWorkspacePanelMetadata.ordinary(), null);
        }

        public OpenAsTab(SFMScreenPanel panel, SFMWorkspacePanelMetadata metadata) {
            this(panel, metadata, null);
        }
    }

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
}
