package ca.teamdman.sfm.client.screen.workspace;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SFMScreenPanelBoundsTests {
    @Test
    public void insetReservesTheMultiplexerFrameOnEveryEdge() {
        assertEquals(
                new SFMScreenPanelBounds(11, 21, 98, 48),
                new SFMScreenPanelBounds(10, 20, 100, 50).inset(1)
        );
    }

    @Test
    public void insetClampsTinyPanelsWithoutNegativeDimensions() {
        assertEquals(
                new SFMScreenPanelBounds(1, 1, 0, 0),
                new SFMScreenPanelBounds(0, 0, 1, 1).inset(1)
        );
    }
}
