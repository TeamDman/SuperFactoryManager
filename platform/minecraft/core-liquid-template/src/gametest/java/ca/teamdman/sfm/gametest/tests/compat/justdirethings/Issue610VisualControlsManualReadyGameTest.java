package ca.teamdman.sfm.gametest.tests.compat.justdirethings;

import ca.teamdman.sfm.gametest.SFMGameTest;

/** The same before/fixed circuit with the physical manual lever visibly preselected. */
@SFMGameTest
public final class Issue610VisualControlsManualReadyGameTest extends Issue610VisualControlsGameTest {
    @Override public String testName() { return "issue_610_visual_controls_manual_ready"; }

    @Override protected boolean initialManualMode() { return true; }
}
