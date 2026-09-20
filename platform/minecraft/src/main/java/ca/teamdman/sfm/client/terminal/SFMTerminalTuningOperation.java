package ca.teamdman.sfm.client.terminal;

/** Shared typed terminal-tuning operation used by actions and panel controls. */
public enum SFMTerminalTuningOperation {
    SURFACE_AUTO,
    SURFACE_SET,
    SURFACE_WIDTH_INCREASE,
    SURFACE_WIDTH_DECREASE,
    SURFACE_HEIGHT_INCREASE,
    SURFACE_HEIGHT_DECREASE,
    FONT_AUTO,
    FONT_SET,
    FONT_INCREASE,
    FONT_DECREASE,
    CELLS_AUTO,
    CELLS_SET,
    COLUMNS_INCREASE,
    COLUMNS_DECREASE,
    ROWS_INCREASE,
    ROWS_DECREASE;

    public SFMTerminalTuningSettings apply(
            SFMTerminalTuningSettings current,
            SFMTerminalTuningSettings.Effective effective,
            int acceptedFontPixelSize,
            int first,
            int second
    ) {
        int fontBaseline = acceptedFontPixelSize > 0
                ? acceptedFontPixelSize
                : SFMTerminalTuningSettings.MIN_FONT_PIXEL_SIZE;
        return switch (this) {
            case SURFACE_AUTO -> current.withSurface(0, 0);
            case SURFACE_SET -> current.withSurface(first, second);
            case SURFACE_WIDTH_INCREASE -> current.withSurfaceWidth(
                    Math.addExact(effective.surfaceWidth(), SFMTerminalTuningSettings.SURFACE_STEP));
            case SURFACE_WIDTH_DECREASE -> current.withSurfaceWidth(
                    Math.subtractExact(effective.surfaceWidth(), SFMTerminalTuningSettings.SURFACE_STEP));
            case SURFACE_HEIGHT_INCREASE -> current.withSurfaceHeight(
                    Math.addExact(effective.surfaceHeight(), SFMTerminalTuningSettings.SURFACE_STEP));
            case SURFACE_HEIGHT_DECREASE -> current.withSurfaceHeight(
                    Math.subtractExact(effective.surfaceHeight(), SFMTerminalTuningSettings.SURFACE_STEP));
            case FONT_AUTO -> current.withFontPixelSize(0);
            case FONT_SET -> current.withFontPixelSize(first);
            case FONT_INCREASE -> current.withFontPixelSize(
                    Math.addExact(current.fontPixelSize() == 0 ? fontBaseline : current.fontPixelSize(),
                            SFMTerminalTuningSettings.FONT_STEP));
            case FONT_DECREASE -> current.withFontPixelSize(
                    Math.subtractExact(current.fontPixelSize() == 0 ? fontBaseline : current.fontPixelSize(),
                            SFMTerminalTuningSettings.FONT_STEP));
            case CELLS_AUTO -> current.withCells(0, 0);
            case CELLS_SET -> current.withCells(first, second);
            case COLUMNS_INCREASE -> current.withColumns(
                    Math.addExact(effective.columns(), SFMTerminalTuningSettings.COLUMN_STEP));
            case COLUMNS_DECREASE -> current.withColumns(
                    Math.subtractExact(effective.columns(), SFMTerminalTuningSettings.COLUMN_STEP));
            case ROWS_INCREASE -> current.withRows(
                    Math.addExact(effective.rows(), SFMTerminalTuningSettings.ROW_STEP));
            case ROWS_DECREASE -> current.withRows(
                    Math.subtractExact(effective.rows(), SFMTerminalTuningSettings.ROW_STEP));
        };
    }
}
