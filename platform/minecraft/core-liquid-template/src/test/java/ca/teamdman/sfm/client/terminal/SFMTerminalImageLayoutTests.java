package ca.teamdman.sfm.client.terminal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SFMTerminalImageLayoutTests {
    @Test
    void physicalRasterIsReducedToItsInverseGuiQuadAndCentered() {
        assertEquals(
                new SFMTerminalImageLayout(27, 23, 492, 272),
                SFMTerminalImageLayout.fit(8, 23, 531, 272, 3432, 1896)
        );
    }

    @Test
    void smallerRasterIsNeverEnlarged() {
        assertEquals(
                new SFMTerminalImageLayout(60, 45, 80, 40),
                SFMTerminalImageLayout.fit(10, 20, 180, 90, 80, 40)
        );
    }

    @Test
    void physicalFitInvertsGuiScaleAndLeavesSubCellRemainderAsLetterbox() {
        assertEquals(
                new SFMTerminalImageLayout(20, 24, 385, 204),
                SFMTerminalImageLayout.fitPhysical(
                        8, 23, 409, 207, 1156, 612, 3.0D, 3.0D)
        );
    }
}
