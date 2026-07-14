package ca.teamdman.sfm.gametest.tests.compat.computercraft;

import ca.teamdman.sfm.common.compat.computercraft.SFMNetworkPeripheral;
import ca.teamdman.sfm.common.compat.computercraft.SFMNetworkPeripheralProvider;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

@SFMGameTest
public class ComputerCraftPayloadLimitsGameTest extends SFMGameTestDefinition {
    private static final SFMNetworkPeripheralProvider PROVIDER = new SFMNetworkPeripheralProvider();

    @Override
    public String template() {

        return "33x3x2";
    }

    @Override
    public void run(SFMGameTestHelper helper) {

        for (int x = 0; x < 33; x++) {
            helper.setBlock(new BlockPos(x, 2, 1), SFMBlocks.CABLE.get());
        }
        for (int x = 0; x < 33; x += 2) {
            helper.setBlock(new BlockPos(x, 2, 0), SFMBlocks.MANAGER.get());
        }

        SFMNetworkPeripheral peripheral = (SFMNetworkPeripheral) PROVIDER
                .getPeripheral(helper.getLevel(), helper.absolutePos(new BlockPos(0, 2, 1)), Direction.NORTH)
                .resolve()
                .orElseThrow();
        helper.assertTrue(
                peripheral.getManagerCount() == 17,
                "The manager count did not include every loaded manager in the cable network"
        );
        helper.assertTrue(
                peripheral.getManagers().size() == SFMNetworkPeripheral.MAX_MANAGER_RESULTS,
                "The network peripheral did not bound its manager payload"
        );
        helper.succeed();
    }
}
