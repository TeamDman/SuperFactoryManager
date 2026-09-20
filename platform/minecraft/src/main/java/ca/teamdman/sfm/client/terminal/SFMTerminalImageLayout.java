package ca.teamdman.sfm.client.terminal;

import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;

/** Pure aspect-preserving layout shared by terminal presenters and diagnostics. */
public record SFMTerminalImageLayout(int x, int y, int width, int height) {
    public SFMTerminalImageLayout {
        if (width < 1 || height < 1) {
            throw new IllegalArgumentException("Terminal image layout must be positive");
        }
    }

    public static SFMTerminalImageLayout fit(
            int targetX,
            int targetY,
            int targetWidth,
            int targetHeight,
            int imageWidth,
            int imageHeight
    ) {
        int boundedTargetWidth = Math.max(1, targetWidth);
        int boundedTargetHeight = Math.max(1, targetHeight);
        int boundedImageWidth = Math.max(1, imageWidth);
        int boundedImageHeight = Math.max(1, imageHeight);
        double scale = Math.min(1.0D, Math.min(
                boundedTargetWidth / (double) boundedImageWidth,
                boundedTargetHeight / (double) boundedImageHeight
        ));
        int drawWidth = Math.max(1, (int) Math.floor(boundedImageWidth * scale));
        int drawHeight = Math.max(1, (int) Math.floor(boundedImageHeight * scale));
        return new SFMTerminalImageLayout(
                targetX + (boundedTargetWidth - drawWidth) / 2,
                targetY + (boundedTargetHeight - drawHeight) / 2,
                drawWidth,
                drawHeight
        );
    }

    /**
     * Inverts the measured panel-to-framebuffer transform so a native Rust
     * raster is not stretched merely to consume sub-cell remainder space.
     */
    public static SFMTerminalImageLayout fitPhysical(
            int targetX,
            int targetY,
            int targetWidth,
            int targetHeight,
            int imageWidth,
            int imageHeight,
            double localToPhysicalScaleX,
            double localToPhysicalScaleY
    ) {
        int boundedTargetWidth = Math.max(1, targetWidth);
        int boundedTargetHeight = Math.max(1, targetHeight);
        double scaleX = positiveFinite(localToPhysicalScaleX);
        double scaleY = positiveFinite(localToPhysicalScaleY);
        int nativeLogicalWidth = Math.max(1, (int) Math.round(Math.max(1, imageWidth) / scaleX));
        int nativeLogicalHeight = Math.max(1, (int) Math.round(Math.max(1, imageHeight) / scaleY));
        int drawWidth = Math.min(boundedTargetWidth, nativeLogicalWidth);
        int drawHeight = Math.min(boundedTargetHeight, nativeLogicalHeight);
        return new SFMTerminalImageLayout(
                targetX + (boundedTargetWidth - drawWidth) / 2,
                targetY + (boundedTargetHeight - drawHeight) / 2,
                drawWidth,
                drawHeight
        );
    }

    private static double positiveFinite(double value) {
        return Double.isFinite(value) && value > 0.0D ? value : 1.0D;
    }

    public SFMScreenPanelBounds bounds() {
        return new SFMScreenPanelBounds(x, y, width, height);
    }
}
