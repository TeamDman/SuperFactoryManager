package ca.teamdman.sfm.client.screen.workspace;

import java.util.Objects;

/** Requests a panel can make of its host without mutating Minecraft's global screen. */
public sealed interface SFMWorkspacePanelIntent {
    record Close() implements SFMWorkspacePanelIntent {
    }

    record OpenToSide(
            SFMWorkspaceSide side,
            SFMScreenPanel panel,
            SFMWorkspacePanelMetadata metadata
    ) implements SFMWorkspacePanelIntent {
        public OpenToSide {
            Objects.requireNonNull(side);
            Objects.requireNonNull(panel);
            Objects.requireNonNull(metadata);
        }

        public OpenToSide(SFMWorkspaceSide side, SFMScreenPanel panel) {
            this(side, panel, SFMWorkspacePanelMetadata.ordinary());
        }
    }

    record OpenAsTab(SFMScreenPanel panel, SFMWorkspacePanelMetadata metadata) implements SFMWorkspacePanelIntent {
        public OpenAsTab {
            Objects.requireNonNull(panel);
            Objects.requireNonNull(metadata);
        }

        public OpenAsTab(SFMScreenPanel panel) {
            this(panel, SFMWorkspacePanelMetadata.ordinary());
        }
    }

    record Move(SFMWorkspaceSide side) implements SFMWorkspacePanelIntent {
        public Move {
            Objects.requireNonNull(side);
        }
    }
}
