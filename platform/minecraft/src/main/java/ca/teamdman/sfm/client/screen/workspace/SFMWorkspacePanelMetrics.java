package ca.teamdman.sfm.client.screen.workspace;

/** Exact mapping from one panel-local logical rectangle to framebuffer pixels. */
public record SFMWorkspacePanelMetrics(
        SFMScreenPanelBounds logicalBounds,
        SFMScreenPanelBounds physicalPixelBounds,
        double panelRenderScale,
        double guiToPhysicalScaleX,
        double guiToPhysicalScaleY,
        int framebufferWidth,
        int framebufferHeight,
        int guiWidth,
        int guiHeight
) {
    public static SFMWorkspacePanelMetrics map(
            SFMScreenPanelBounds logicalBounds,
            int guiContentX,
            int guiContentY,
            double panelRenderScale,
            int framebufferWidth,
            int framebufferHeight,
            int guiWidth,
            int guiHeight
    ) {
        int safeGuiWidth = Math.max(1, guiWidth);
        int safeGuiHeight = Math.max(1, guiHeight);
        double scaleX = framebufferWidth / (double) safeGuiWidth;
        double scaleY = framebufferHeight / (double) safeGuiHeight;
        double guiLeft = guiContentX + logicalBounds.x() * panelRenderScale;
        double guiTop = guiContentY + logicalBounds.y() * panelRenderScale;
        double guiRight = guiContentX + (logicalBounds.x() + logicalBounds.width()) * panelRenderScale;
        double guiBottom = guiContentY + (logicalBounds.y() + logicalBounds.height()) * panelRenderScale;
        int physicalLeft = (int) Math.floor(guiLeft * scaleX);
        int physicalTop = (int) Math.floor(guiTop * scaleY);
        int physicalRight = (int) Math.ceil(guiRight * scaleX);
        int physicalBottom = (int) Math.ceil(guiBottom * scaleY);
        return new SFMWorkspacePanelMetrics(
                logicalBounds,
                new SFMScreenPanelBounds(
                        physicalLeft,
                        physicalTop,
                        Math.max(1, physicalRight - physicalLeft),
                        Math.max(1, physicalBottom - physicalTop)
                ),
                panelRenderScale,
                scaleX,
                scaleY,
                framebufferWidth,
                framebufferHeight,
                safeGuiWidth,
                safeGuiHeight
        );
    }

    public double localToPhysicalScaleX() {
        return panelRenderScale * guiToPhysicalScaleX;
    }

    public double localToPhysicalScaleY() {
        return panelRenderScale * guiToPhysicalScaleY;
    }
}
