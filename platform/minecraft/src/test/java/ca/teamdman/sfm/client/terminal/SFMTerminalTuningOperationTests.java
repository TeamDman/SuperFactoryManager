package ca.teamdman.sfm.client.terminal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SFMTerminalTuningOperationTests {
    private static final SFMTerminalTuningSettings.Effective EFFECTIVE =
            new SFMTerminalTuningSettings.Effective(1600, 900, 0, 120, 40);

    @Test
    void incrementsPromoteAutomaticDimensionsToExplicitOverrides() {
        SFMTerminalTuningSettings automatic = SFMTerminalTuningSettings.automatic();

        assertEquals(1664, apply(SFMTerminalTuningOperation.SURFACE_WIDTH_INCREASE, automatic).surfaceWidth());
        assertEquals(124, apply(SFMTerminalTuningOperation.COLUMNS_INCREASE, automatic).columns());
        assertEquals(25, apply(SFMTerminalTuningOperation.FONT_INCREASE, automatic).fontPixelSize());
    }

    @Test
    void autoOperationsOnlyClearTheirOwnAxis() {
        SFMTerminalTuningSettings explicit = new SFMTerminalTuningSettings(1600, 900, 24, 100, 30);

        assertEquals(new SFMTerminalTuningSettings(0, 0, 24, 100, 30),
                apply(SFMTerminalTuningOperation.SURFACE_AUTO, explicit));
        assertEquals(new SFMTerminalTuningSettings(1600, 900, 0, 100, 30),
                apply(SFMTerminalTuningOperation.FONT_AUTO, explicit));
        assertEquals(new SFMTerminalTuningSettings(1600, 900, 24, 0, 0),
                apply(SFMTerminalTuningOperation.CELLS_AUTO, explicit));
    }

    @Test
    void outOfRangeChangesAreRejectedRatherThanClamped() {
        SFMTerminalTuningSettings minimum = new SFMTerminalTuningSettings(1, 1, 8, 1, 1);
        SFMTerminalTuningSettings.Effective minimumEffective =
                new SFMTerminalTuningSettings.Effective(1, 1, 8, 1, 1);

        assertThrows(IllegalArgumentException.class, () ->
                SFMTerminalTuningOperation.SURFACE_WIDTH_DECREASE.apply(
                        minimum, minimumEffective, 8, 0, 0));
        assertThrows(IllegalArgumentException.class, () ->
                SFMTerminalTuningOperation.FONT_DECREASE.apply(
                        minimum, minimumEffective, 8, 0, 0));
        assertThrows(IllegalArgumentException.class, () ->
                SFMTerminalTuningOperation.COLUMNS_DECREASE.apply(
                        minimum, minimumEffective, 8, 0, 0));
    }

    private static SFMTerminalTuningSettings apply(
            SFMTerminalTuningOperation operation,
            SFMTerminalTuningSettings settings
    ) {
        return operation.apply(settings, EFFECTIVE, 24, 0, 0);
    }
}
