package ca.teamdman.sfm.gametest.tests.compat.computercraft;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
{% case minecraft_version %}
{% when '1.19.2' %}
{% when '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
{% endcase %}
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
{% case minecraft_version %}
{% when '1.19.2' %}
import dan200.computercraft.shared.Registry;
{% when '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
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

import java.util.Objects;

/**
 * Moves items into a live CC:Tweaked turtle through an SFM cable network.
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
public class ComputerCraftTurtleCableNetworkGameTest extends SFMGameTestDefinition {
    @Override
    public String template() {

        return "7x3x1";
    }

    @Override
    public void run(SFMGameTestHelper helper) {

        BlockPos sourcePos = new BlockPos(0, 2, 0);
        BlockPos firstCablePos = new BlockPos(1, 2, 0);
        BlockPos managerPos = new BlockPos(2, 2, 0);
        BlockPos secondCablePos = new BlockPos(3, 2, 0);
        BlockPos connectedTurtlePos = new BlockPos(4, 2, 0);
        BlockPos disconnectedTurtlePos = new BlockPos(6, 2, 0);

        helper.setBlock(sourcePos, SFMBlocks.TEST_BARREL.get());
        helper.setBlock(firstCablePos, SFMBlocks.CABLE.get());
        helper.setBlock(managerPos, SFMBlocks.MANAGER.get());
        helper.setBlock(secondCablePos, SFMBlocks.CABLE.get());
{% case minecraft_version %}
{% when '1.19.2' %}
        helper.setBlock(connectedTurtlePos, Registry.ModBlocks.TURTLE_NORMAL.get());
        helper.setBlock(disconnectedTurtlePos, Registry.ModBlocks.TURTLE_NORMAL.get());
{% when '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
        helper.setBlock(connectedTurtlePos, ModRegistry.Blocks.TURTLE_NORMAL.get());
        helper.setBlock(disconnectedTurtlePos, ModRegistry.Blocks.TURTLE_NORMAL.get());
{% endcase %}

        IItemHandler source = helper.getItemHandler(sourcePos);
        IItemHandler connectedTurtle = helper.getItemHandler(connectedTurtlePos);
        IItemHandler disconnectedTurtle = helper.getItemHandler(disconnectedTurtlePos);
        helper.assertTrue(
                source.insertItem(0, new ItemStack(Items.DIRT, 16), false).isEmpty(),
                "Could not prepare the cable-network source inventory"
        );

        ManagerBlockEntity manager = helper.getBlockEntity(managerPos, ManagerBlockEntity.class);
        manager.setItem(0, new ItemStack(SFMItems.DISK.get()));
        LabelPositionHolder.empty()
                .add("source", helper.absolutePos(sourcePos))
                .add("connected", helper.absolutePos(connectedTurtlePos))
                .add("disconnected", helper.absolutePos(disconnectedTurtlePos))
                .save(Objects.requireNonNull(manager.getDisk()));
        manager.setProgram("""
                EVERY 20 TICKS DO
                    INPUT FROM source
                    OUTPUT TO disconnected
                    OUTPUT TO connected
                END
                """.stripTrailing().stripIndent());

        helper.succeedIfManagerDidThingWithoutLagging(manager, () -> {
            helper.assertCount(source, Items.DIRT, 0, "Source inventory was not emptied through the cable network");
            helper.assertCount(
                    connectedTurtle,
                    Items.DIRT,
                    16,
                    "Connected turtle did not receive items through the SFM cable network"
            );
            helper.assertCount(
                    disconnectedTurtle,
                    Items.DIRT,
                    0,
                    "Disconnected turtle was incorrectly selected by the cable-network transfer"
            );
            helper.assertManagerRunning(manager);
        });
    }
}
