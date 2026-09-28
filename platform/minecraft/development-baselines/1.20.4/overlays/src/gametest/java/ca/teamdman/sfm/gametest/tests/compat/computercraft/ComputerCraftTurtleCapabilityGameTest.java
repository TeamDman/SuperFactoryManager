package ca.teamdman.sfm.gametest.tests.compat.computercraft;

import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import dan200.computercraft.shared.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Proves SFM's normal capability discovery sees a live CC:Tweaked turtle inventory.
 */
@SFMGameTest
@MCVersionDependentBehaviour // CC:Tweaked 1.110.2+ internal GameTest fixture API
public class ComputerCraftTurtleCapabilityGameTest extends SFMGameTestDefinition {
    @Override
    public String template() {

        return "3x3x1";
    }

    @Override
    public void run(SFMGameTestHelper helper) {

        BlockPos turtlePos = new BlockPos(1, 2, 0);
        helper.setBlock(turtlePos, ModRegistry.Blocks.TURTLE_NORMAL.get());

        IItemHandler turtleInventory = helper.getItemHandler(turtlePos);
        helper.assertTrue(
                turtleInventory.getSlots() == 16,
                "SFM did not discover the normal turtle's sixteen-slot item handler"
        );
        ItemStack remainder = turtleInventory.insertItem(0, new ItemStack(Items.DIRT, 4), false);
        helper.assertTrue(remainder.isEmpty(), "Could not insert into the turtle item handler discovered by SFM");
        helper.assertTrue(
                turtleInventory.getStackInSlot(0).getCount() == 4,
                "Turtle item handler did not retain the stack inserted through SFM discovery"
        );

        helper.succeed();
    }
}
