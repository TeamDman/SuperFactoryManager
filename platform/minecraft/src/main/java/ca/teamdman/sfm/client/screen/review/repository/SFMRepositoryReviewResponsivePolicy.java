package ca.teamdman.sfm.client.screen.review.repository;

import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspaceLayout;

import static ca.teamdman.sfm.client.screen.workspace.SFMWorkspaceLayout.horizontal;
import static ca.teamdman.sfm.client.screen.workspace.SFMWorkspaceLayout.panel;
import static ca.teamdman.sfm.client.screen.workspace.SFMWorkspaceLayout.stack;

/** Pure logical-GUI breakpoint policy; it does not inspect physical/framebuffer dimensions. */
public final class SFMRepositoryReviewResponsivePolicy {
    public static final int WIDE_MINIMUM_WIDTH = 1000;
    public static final int MEDIUM_MINIMUM_WIDTH = 700;

    public enum Mode { WIDE, MEDIUM, NARROW }

    private SFMRepositoryReviewResponsivePolicy() {}

    public static Mode mode(SFMScreenPanelBounds bounds) {
        if (bounds.width() >= WIDE_MINIMUM_WIDTH) return Mode.WIDE;
        if (bounds.width() >= MEDIUM_MINIMUM_WIDTH) return Mode.MEDIUM;
        return Mode.NARROW;
    }

    public static SFMWorkspaceLayout.LayoutSpec layout(
            SFMScreenPanelBounds bounds,
            SFMRepositoryReviewWorkspaceModel.Blade active,
            SFMScreenPanel files,
            SFMScreenPanel before,
            SFMScreenPanel after,
            SFMScreenPanel comments
    ) {
        return switch (mode(bounds)) {
            case WIDE -> horizontal(panel(files), panel(before),
                    stack(active == SFMRepositoryReviewWorkspaceModel.Blade.COMMENTS ? 1 : 0,
                            panel(after), panel(comments)));
            case MEDIUM -> horizontal(panel(files), stack(sourceStackIndex(active),
                    panel(before), panel(after), panel(comments)));
            case NARROW -> stack(active.ordinal(), panel(files), panel(before), panel(after), panel(comments));
        };
    }

    private static int sourceStackIndex(SFMRepositoryReviewWorkspaceModel.Blade active) {
        return switch (active) {
            case BEFORE -> 0;
            case COMMENTS -> 2;
            case FILES, AFTER -> 1;
        };
    }
}
