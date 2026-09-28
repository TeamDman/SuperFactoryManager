package ca.teamdman.sfm.client.screen.workspace;

import java.util.Objects;

/** Requests a panel can make of its host without mutating Minecraft's global screen. */
public sealed interface SFMWorkspacePanelIntent {
    record Close() implements SFMWorkspacePanelIntent {
    }

    record OpenToSide(SFMWorkspaceSide side, SFMScreenPanel panel) implements SFMWorkspacePanelIntent {
        public OpenToSide {
            Objects.requireNonNull(side);
            Objects.requireNonNull(panel);
        }
    }

    record OpenAsTab(SFMScreenPanel panel) implements SFMWorkspacePanelIntent {
        public OpenAsTab {
            Objects.requireNonNull(panel);
        }
    }
}
