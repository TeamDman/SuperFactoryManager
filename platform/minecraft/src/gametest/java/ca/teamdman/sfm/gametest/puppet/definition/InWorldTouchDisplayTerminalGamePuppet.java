package ca.teamdman.sfm.gametest.puppet.definition;

import ca.teamdman.sfm.gametest.puppet.SFMGamePuppet;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetViewportProfile;
import ca.teamdman.sfm.gametest.tests.general.TouchDisplayTerminalIntegrationGameTest;
import ca.teamdman.sfm.gametest.tests.general.TouchDisplayTerminalVisualControl;

/** File-driven P10 world-pixel proof. Never discovered by ambient run-all GameTests. */
@SFMGamePuppet(viewportProfile = SFMGamePuppetViewportProfile.COMMON_RESPONSIVE, timeoutTicks = 20 * 60 * 15)
public final class InWorldTouchDisplayTerminalGamePuppet {
    private InWorldTouchDisplayTerminalGamePuppet() {}

    public static void run(SFMGamePuppetHelper puppet) {
        var control = new TouchDisplayTerminalVisualControl();
        var fixture = new TouchDisplayTerminalIntegrationGameTest(control);
        puppet.createFreshFlatWorld();
        puppet.startGameTest(fixture);
        puppet.waitTicks(20);
        puppet.exploreTouchDisplayTerminalInteractively(control);
        puppet.waitForGameTest(fixture.testName());
    }
}
