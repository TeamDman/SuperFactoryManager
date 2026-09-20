package ca.teamdman.sfm.client.screen.workspace;

import java.util.Objects;

/** One divider with both GUI-logical and framebuffer-physical hit geometry. */
public record SFMWorkspaceDividerView(
        SFMWorkspaceDivider logical,
        SFMScreenPanelBounds physicalLineBounds,
        SFMScreenPanelBounds physicalHitBounds
) {
    public SFMWorkspaceDividerView {
        Objects.requireNonNull(logical, "logical");
        Objects.requireNonNull(physicalLineBounds, "physicalLineBounds");
        Objects.requireNonNull(physicalHitBounds, "physicalHitBounds");
    }

    public static SFMWorkspaceDividerView scale(
            SFMWorkspaceDivider divider,
            SFMScreenPanelBounds logicalViewport,
            int framebufferWidth,
            int framebufferHeight
    ) {
        return new SFMWorkspaceDividerView(
                divider,
                scale(divider.lineBounds(), logicalViewport, framebufferWidth, framebufferHeight),
                scale(divider.hitBounds(), logicalViewport, framebufferWidth, framebufferHeight)
        );
    }

    private static SFMScreenPanelBounds scale(
            SFMScreenPanelBounds bounds,
            SFMScreenPanelBounds logicalViewport,
            int framebufferWidth,
            int framebufferHeight
    ) {
        if (logicalViewport.width() <= 0 || logicalViewport.height() <= 0) {
            return new SFMScreenPanelBounds(0, 0, 0, 0);
        }
        double scaleX = framebufferWidth / (double) logicalViewport.width();
        double scaleY = framebufferHeight / (double) logicalViewport.height();
        int left = (int) Math.floor((bounds.x() - logicalViewport.x()) * scaleX);
        int top = (int) Math.floor((bounds.y() - logicalViewport.y()) * scaleY);
        int right = (int) Math.ceil((bounds.x() + bounds.width() - logicalViewport.x()) * scaleX);
        int bottom = (int) Math.ceil((bounds.y() + bounds.height() - logicalViewport.y()) * scaleY);
        return new SFMScreenPanelBounds(left, top, Math.max(0, right - left), Math.max(0, bottom - top));
    }
}
