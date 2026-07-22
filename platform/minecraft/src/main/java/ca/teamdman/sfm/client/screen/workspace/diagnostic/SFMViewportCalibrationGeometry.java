package ca.teamdman.sfm.client.screen.workspace.diagnostic;

import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;

import java.util.ArrayList;
import java.util.List;

/** Pure geometry plan shared by rendering and allocation tests. */
public record SFMViewportCalibrationGeometry(
        List<ColourBar> colourBars,
        List<Checker> checkerboard,
        List<Line> markers,
        Pointer pointer
) {
    private static final int[] BAR_COLOURS = {
            0xFFFFFFFF, 0xFFFFFF00, 0xFF00FFFF, 0xFF00FF00,
            0xFFFF00FF, 0xFFFF0000, 0xFF0000FF
    };

    public SFMViewportCalibrationGeometry {
        colourBars = List.copyOf(colourBars);
        checkerboard = List.copyOf(checkerboard);
        markers = List.copyOf(markers);
    }

    public static SFMViewportCalibrationGeometry create(
            SFMScreenPanelBounds bounds,
            int mouseX,
            int mouseY
    ) {
        List<ColourBar> bars = new ArrayList<>();
        int barHeight = Math.max(1, Math.min(24, bounds.height() / 5));
        for (int index = 0; index < BAR_COLOURS.length; index++) {
            int x0 = bounds.x() + bounds.width() * index / BAR_COLOURS.length;
            int x1 = bounds.x() + bounds.width() * (index + 1) / BAR_COLOURS.length;
            bars.add(new ColourBar(new SFMScreenPanelBounds(x0, bounds.y(), Math.max(0, x1 - x0), barHeight), BAR_COLOURS[index]));
        }

        List<Checker> checkerboard = new ArrayList<>();
        if (bounds.width() > 0 && bounds.height() > 0) {
            for (int x = bounds.x(); x < bounds.x() + bounds.width(); x++) {
                checkerboard.add(new Checker(x, bounds.y() + bounds.height() - 1, ((x - bounds.x()) & 1) == 0));
            }
            for (int y = bounds.y(); y < bounds.y() + bounds.height(); y++) {
                checkerboard.add(new Checker(bounds.x(), y, ((y - bounds.y()) & 1) == 0));
            }
        }

        List<Line> markers = new ArrayList<>();
        if (bounds.width() > 0 && bounds.height() > 0) {
            int left = bounds.x();
            int top = bounds.y();
            int right = bounds.x() + bounds.width() - 1;
            int bottom = bounds.y() + bounds.height() - 1;
            int centerX = bounds.x() + bounds.width() / 2;
            int centerY = bounds.y() + bounds.height() / 2;
            int arm = Math.max(2, Math.min(8, Math.min(bounds.width(), bounds.height()) / 8));
            markers.add(new Line(centerX - arm, centerY, centerX + arm + 1, centerY + 1));
            markers.add(new Line(centerX, centerY - arm, centerX + 1, centerY + arm + 1));
            markers.addAll(corner(left, top, 1, 1, arm));
            markers.addAll(corner(right, top, -1, 1, arm));
            markers.addAll(corner(left, bottom, 1, -1, arm));
            markers.addAll(corner(right, bottom, -1, -1, arm));
        }
        return new SFMViewportCalibrationGeometry(
                bars,
                checkerboard,
                markers,
                new Pointer(mouseX, mouseY, mouseX - bounds.x(), mouseY - bounds.y(), bounds.contains(mouseX, mouseY))
        );
    }

    private static List<Line> corner(int x, int y, int dx, int dy, int arm) {
        return List.of(
                normalizedLine(x, y, x + dx * arm, y),
                normalizedLine(x, y, x, y + dy * arm)
        );
    }

    private static Line normalizedLine(int x0, int y0, int x1, int y1) {
        return new Line(Math.min(x0, x1), Math.min(y0, y1), Math.max(x0, x1) + 1, Math.max(y0, y1) + 1);
    }

    public record ColourBar(SFMScreenPanelBounds bounds, int argb) {
    }

    public record Checker(int x, int y, boolean light) {
    }

    public record Line(int x0, int y0, int x1, int y1) {
    }

    public record Pointer(int screenX, int screenY, int localX, int localY, boolean inside) {
    }
}
