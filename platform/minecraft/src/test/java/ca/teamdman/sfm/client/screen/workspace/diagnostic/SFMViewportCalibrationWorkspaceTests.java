package ca.teamdman.sfm.client.screen.workspace.diagnostic;

import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SFMViewportCalibrationWorkspaceTests {
    private static final SFMScreenPanelBounds VIEWPORT = new SFMScreenPanelBounds(0, 0, 1202, 722);

    @Test
    void fullAndHalfUseTheSharedWorkspaceAllocator() {
        var full = SFMViewportCalibrationWorkspace.layout(SFMViewportCalibrationWorkspace.Allocation.FULL);
        assertEquals(VIEWPORT, full.bounds(VIEWPORT, 2).get(full.panels().get(0).id()));

        var half = SFMViewportCalibrationWorkspace.layout(SFMViewportCalibrationWorkspace.Allocation.HALF);
        var bounds = half.bounds(VIEWPORT, 2);
        assertEquals(new SFMScreenPanelBounds(0, 0, 600, 722), bounds.get(half.panels().get(0).id()));
        assertEquals(new SFMScreenPanelBounds(602, 0, 600, 722), bounds.get(half.panels().get(1).id()));
    }

    @Test
    void thirdsAreEqualPeersRatherThanHandCalculatedRectangles() {
        var thirds = SFMViewportCalibrationWorkspace.layout(SFMViewportCalibrationWorkspace.Allocation.THIRD);
        var bounds = thirds.bounds(VIEWPORT, 2);

        assertEquals(3, thirds.panels().size());
        assertEquals(399, bounds.get(thirds.panels().get(0).id()).width());
        assertEquals(399, bounds.get(thirds.panels().get(1).id()).width());
        assertEquals(400, bounds.get(thirds.panels().get(2).id()).width());
    }

    @Test
    void nestedFixtureExercisesBothWorkspaceAxes() {
        var nested = SFMViewportCalibrationWorkspace.layout(SFMViewportCalibrationWorkspace.Allocation.NESTED);
        var bounds = nested.bounds(VIEWPORT, 2);

        assertEquals(new SFMScreenPanelBounds(0, 0, 600, 722), bounds.get(nested.panels().get(0).id()));
        assertEquals(new SFMScreenPanelBounds(602, 0, 600, 360), bounds.get(nested.panels().get(1).id()));
        assertEquals(new SFMScreenPanelBounds(602, 362, 600, 360), bounds.get(nested.panels().get(2).id()));
    }
}
