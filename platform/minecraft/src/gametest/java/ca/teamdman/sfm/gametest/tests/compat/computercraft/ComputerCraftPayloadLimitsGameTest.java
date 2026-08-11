package ca.teamdman.sfm.gametest.tests.compat.computercraft;

import ca.teamdman.sfm.common.compat.computercraft.SFMManagerCollectionHandle;
import ca.teamdman.sfm.common.compat.computercraft.SFMNetworkPeripheral;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import dan200.computercraft.api.peripheral.PeripheralCapability;

import java.util.Objects;

/** Ensures manager discovery is no longer bounded by the former table-payload cap. */
@SFMGameTest
public class ComputerCraftPayloadLimitsGameTest extends SFMGameTestDefinition {
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

        SFMNetworkPeripheral peripheral = (SFMNetworkPeripheral) Objects.requireNonNull(
                helper.getLevel().getCapability(
                        PeripheralCapability.get(),
                        helper.absolutePos(new BlockPos(0, 2, 1)),
                        Direction.NORTH
                ),
                "SFM cable did not expose an SFM network peripheral"
        );
        SFMManagerCollectionHandle managers = peripheral.getManagers();
        helper.assertTrue(managers.count() == 17, "Manager collection did not include every loaded manager");
        helper.assertTrue(
                managers.get(17).position().length == 3,
                "Manager collection did not expose its final entry"
        );
        helper.succeed();
    }
}
