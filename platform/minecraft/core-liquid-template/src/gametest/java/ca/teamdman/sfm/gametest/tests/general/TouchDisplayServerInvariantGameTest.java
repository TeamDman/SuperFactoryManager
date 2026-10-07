package ca.teamdman.sfm.gametest.tests.general;

import ca.teamdman.sfm.common.item.PacketItem;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;

/** Runs with or without a client: server use, mailbox movement, IMAGE:: commit and immutable touch evidence. */
@SFMGameTest
public final class TouchDisplayServerInvariantGameTest extends SFMGameTestDefinition {
    @Override public String template() { return "11x3x3"; }
    @Override public int maxTicks() { return 200; }

    @Override public void run(SFMGameTestHelper helper) {
        var fixture = new TouchDisplayCircuitFixture(helper, null, null);
        var touch = fixture.press();
        helper.assertTrue(helper.getItemHandler(TouchDisplayCircuitFixture.COMMAND_MAILBOX)
                                  .insertItem(0, PacketItem.create(TouchDisplayCircuitFixture.COMMAND), false).isEmpty(),
                "Could not seed the server-invariant command (client delivery is covered separately)");
        helper.succeedWhen(() -> fixture.assertCompleted(touch));
    }
}
