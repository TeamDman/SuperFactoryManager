package ca.teamdman.sfm.client.screen.workspace.diagnostic;

import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;

import java.util.Objects;

/** Pure geometry for a solid allocation card and its centered logical size. */
public record SFMSizeDisplayGeometry(
        SFMScreenPanelBounds background,
        int textX,
        int textY,
        int textColour,
        String text
) {
    public SFMSizeDisplayGeometry {
        Objects.requireNonNull(background);
        Objects.requireNonNull(text);
    }

    public static SFMSizeDisplayGeometry create(
            SFMScreenPanelBounds bounds,
            String text,
            int textWidth,
            int textHeight,
            int backgroundColour
    ) {
        Objects.requireNonNull(bounds);
        Objects.requireNonNull(text);
        if (textWidth < 0 || textHeight < 0) {
            throw new IllegalArgumentException("Text dimensions must not be negative");
        }
        int opaqueBackground = backgroundColour | 0xFF000000;
        return new SFMSizeDisplayGeometry(
                bounds,
                bounds.x() + (bounds.width() - textWidth) / 2,
                bounds.y() + (bounds.height() - textHeight) / 2,
                accessibleTextColour(opaqueBackground),
                text
        );
    }

    /** Choose whichever opaque black/white foreground has the better WCAG contrast ratio. */
    public static int accessibleTextColour(int backgroundColour) {
        double luminance = relativeLuminance(backgroundColour | 0xFF000000);
        double whiteContrast = (1.0 + 0.05) / (luminance + 0.05);
        double blackContrast = (luminance + 0.05) / 0.05;
        return whiteContrast >= blackContrast ? 0xFFFFFFFF : 0xFF000000;
    }

    private static double relativeLuminance(int argb) {
        double red = linearChannel((argb >>> 16) & 0xFF);
        double green = linearChannel((argb >>> 8) & 0xFF);
        double blue = linearChannel(argb & 0xFF);
        return 0.2126 * red + 0.7152 * green + 0.0722 * blue;
    }

    private static double linearChannel(int channel) {
        double normalized = channel / 255.0;
        return normalized <= 0.03928
                ? normalized / 12.92
                : Math.pow((normalized + 0.055) / 1.055, 2.4);
    }
}
