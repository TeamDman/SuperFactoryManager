package ca.teamdman.sfm.client.terminal;

/**
 * Panel-local terminal tuning. Zero means that one dimension follows the
 * current workspace allocation; positive values are explicit user overrides.
 */
public record SFMTerminalTuningSettings(
        int surfaceWidth,
        int surfaceHeight,
        int fontPixelSize,
        int columns,
        int rows
) {
    public static final int SURFACE_STEP = 64;
    public static final int FONT_STEP = 1;
    public static final int COLUMN_STEP = 4;
    public static final int ROW_STEP = 2;
    public static final int MIN_FONT_PIXEL_SIZE = 8;
    public static final int MAX_FONT_PIXEL_SIZE = 64;
    public static final int MAX_COLUMNS = 240;
    public static final int MAX_ROWS = 120;

    public SFMTerminalTuningSettings {
        requireAutoOrRange(surfaceWidth, 1, SFMTerminalRasterLimits.RGBA8_V1_MAX_WIDTH, "surfaceWidth");
        requireAutoOrRange(surfaceHeight, 1, SFMTerminalRasterLimits.RGBA8_V1_MAX_HEIGHT, "surfaceHeight");
        requireAutoOrRange(fontPixelSize, MIN_FONT_PIXEL_SIZE, MAX_FONT_PIXEL_SIZE, "fontPixelSize");
        requireAutoOrRange(columns, 1, MAX_COLUMNS, "columns");
        requireAutoOrRange(rows, 1, MAX_ROWS, "rows");
    }

    public static SFMTerminalTuningSettings automatic() {
        return new SFMTerminalTuningSettings(0, 0, 0, 0, 0);
    }

    public Effective resolve(
            int automaticSurfaceWidth,
            int automaticSurfaceHeight,
            int automaticColumns,
            int automaticRows
    ) {
        return new Effective(
                surfaceWidth == 0
                        ? clamp(automaticSurfaceWidth, 1, SFMTerminalRasterLimits.RGBA8_V1_MAX_WIDTH)
                        : surfaceWidth,
                surfaceHeight == 0
                        ? clamp(automaticSurfaceHeight, 1, SFMTerminalRasterLimits.RGBA8_V1_MAX_HEIGHT)
                        : surfaceHeight,
                fontPixelSize,
                columns == 0 ? clamp(automaticColumns, 1, MAX_COLUMNS) : columns,
                rows == 0 ? clamp(automaticRows, 1, MAX_ROWS) : rows
        );
    }

    public SFMTerminalTuningSettings withSurface(int width, int height) {
        return new SFMTerminalTuningSettings(width, height, fontPixelSize, columns, rows);
    }

    public SFMTerminalTuningSettings withSurfaceWidth(int width) {
        return withSurface(width, surfaceHeight);
    }

    public SFMTerminalTuningSettings withSurfaceHeight(int height) {
        return withSurface(surfaceWidth, height);
    }

    public SFMTerminalTuningSettings withFontPixelSize(int pixels) {
        return new SFMTerminalTuningSettings(surfaceWidth, surfaceHeight, pixels, columns, rows);
    }

    public SFMTerminalTuningSettings withCells(int newColumns, int newRows) {
        return new SFMTerminalTuningSettings(surfaceWidth, surfaceHeight, fontPixelSize, newColumns, newRows);
    }

    public SFMTerminalTuningSettings withColumns(int newColumns) {
        return withCells(newColumns, rows);
    }

    public SFMTerminalTuningSettings withRows(int newRows) {
        return withCells(columns, newRows);
    }

    private static void requireAutoOrRange(int value, int minimum, int maximum, String name) {
        if (value != 0 && (value < minimum || value > maximum)) {
            throw new IllegalArgumentException(
                    name + " must be auto (0) or within " + minimum + ".." + maximum);
        }
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(value, maximum));
    }

    public record Effective(
            int surfaceWidth,
            int surfaceHeight,
            int fontPixelSize,
            int columns,
            int rows
    ) {
        public Effective {
            if (surfaceWidth <= 0 || surfaceHeight <= 0 || columns <= 0 || rows <= 0) {
                throw new IllegalArgumentException("Effective terminal dimensions must be positive");
            }
        }
    }
}
