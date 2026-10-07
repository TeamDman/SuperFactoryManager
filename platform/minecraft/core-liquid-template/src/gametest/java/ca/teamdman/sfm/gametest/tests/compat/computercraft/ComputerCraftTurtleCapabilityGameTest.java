package ca.teamdman.sfm.gametest.tests.compat.computercraft;

import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
{% case minecraft_version %}
{% when '1.19.2' %}
import dan200.computercraft.shared.Registry;
{% when '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import dan200.computercraft.shared.ModRegistry;
{% endcase %}
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3' %}
import net.minecraftforge.items.IItemHandler;
{% when '1.20.4', '1.21', '1.21.1', '26.1.2' %}
import net.neoforged.neoforge.items.IItemHandler;
{% endcase %}

/**
 * Proves SFM's normal capability discovery sees a live CC:Tweaked turtle inventory.
 */
@SFMGameTest
{% case minecraft_version %}
{% when '1.19.2' %}
{% when '1.19.4', '1.20' %}
@MCVersionDependentBehaviour // CC internal GameTest fixture API
{% when '1.20.1', '1.20.2', '1.20.3', '1.21' %}
@MCVersionDependentBehaviour // CC:Tweaked 1.111.0+ internal GameTest fixture API
{% when '1.20.4' %}
@MCVersionDependentBehaviour // CC:Tweaked 1.110.2+ internal GameTest fixture API
{% when '1.21.1', '26.1.2' %}
@MCVersionDependentBehaviour // CC:Tweaked 1.113.1+ internal GameTest fixture API
{% endcase %}
public class ComputerCraftTurtleCapabilityGameTest extends SFMGameTestDefinition {
    @Override
    public String template() {

        return "3x3x1";
    }

    @Override
    public void run(SFMGameTestHelper helper) {

        BlockPos turtlePos = new BlockPos(1, 2, 0);
{% case minecraft_version %}
{% when '1.19.2' %}
        helper.setBlock(turtlePos, Registry.ModBlocks.TURTLE_NORMAL.get());
{% when '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
        helper.setBlock(turtlePos, ModRegistry.Blocks.TURTLE_NORMAL.get());
{% endcase %}

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
