package ca.teamdman.sfm.gametest.tests.compat.mekanism;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.capability.SFMWellKnownCapabilities;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import mekanism.common.registries.MekanismBlocks;
import net.minecraft.core.BlockPos;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3' %}
{% when '1.20.4', '1.21', '1.21.1' %}
import net.minecraft.core.Direction;
{% when '26.1.2' %}
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
{% endcase %}
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3' %}
import net.minecraftforge.fluids.FluidStack;
{% when '1.20.4', '1.21', '1.21.1' %}
import net.neoforged.neoforge.fluids.FluidStack;
{% when '26.1.2' %}
{% endcase %}

import java.util.ArrayList;
import java.util.Objects;




/**
 * Migrated from SFMMekanismCompatGameTests.many_lava_cauldrons
 */
@SuppressWarnings({
        "RedundantSuppression",
        "DataFlowIssue",
        "OptionalGetWithoutIsPresent",
        "DuplicatedCode",
        "ArraysAsListWithZeroOrOneArgument"
})
@SFMGameTest
public class MekManyLavaCauldronsGameTest extends SFMGameTestDefinition {

    @Override
    public String template() {
        return "25x3x25";
    }


    @Override
    public void run(SFMGameTestHelper helper) {
        // designate positions
        var sourceBlocks = new ArrayList<BlockPos>();
        var destBlocks = new ArrayList<BlockPos>();
        var managerPos = new BlockPos(0, 2, 0);

        // set up cauldrons
        for (int x = 0; x < 25; x++) {
            for (int z = 1; z < 25; z++) {
                helper.setBlock(new BlockPos(x, 2, z), SFMBlocks.CABLE.get());
                helper.setBlock(new BlockPos(x, 3, z), Blocks.LAVA_CAULDRON);
                sourceBlocks.add(new BlockPos(x, 3, z));
            }
        }

        // set up tanks
        for (int i = 1; i < 25; i++) {
            BlockPos tankPos = new BlockPos(i, 2, 0);
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
            helper.setBlock(tankPos, MekanismBlocks.BASIC_FLUID_TANK.getBlock());
{% when '26.1.2' %}
            helper.setBlock(tankPos, MekanismBlocks.BASIC_FLUID_TANK.get());
{% endcase %}
            destBlocks.add(tankPos);
        }

        // set up the manager
        helper.setBlock(managerPos, SFMBlocks.MANAGER.get());
        ManagerBlockEntity manager = helper.getBlockEntity(managerPos, ManagerBlockEntity.class);
        manager.setItem(0, new ItemStack(SFMItems.DISK.get()));

        // create the program
        var program = """
                    NAME "many inventory lag test"
                                
                    EVERY 20 TICKS DO
                        INPUT fluid:*:* FROM source
                        OUTPUT fluid:*:* TO dest TOP SIDE
                    END
                """;

        // set the labels
        LabelPositionHolder.empty()
                .addAll("source", sourceBlocks.stream().map(helper::absolutePos).toList())
                .addAll("dest", destBlocks.stream().map(helper::absolutePos).toList())
                .save(manager.getDisk());

        // load the program
        manager.setProgram(program);
        helper.succeedIfManagerDidThingWithoutLagging(manager, () -> {
            sourceBlocks.forEach(pos -> helper.assertBlock(
                    pos,
                    Blocks.CAULDRON::equals,
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
                    () -> "Cauldron did not empty"
{% when '26.1.2' %}
                    (_) -> Component.literal("Cauldron did not empty")
{% endcase %}
            ));
            int found = destBlocks
                    .stream()
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3' %}
                    .map(helper::getBlockEntity)
                    .map(be -> be.getCapability(SFMWellKnownCapabilities.FLUID_HANDLER.capabilityKind()))
                    .map(x -> x.orElse(null))
{% when '1.20.4', '1.21', '1.21.1', '26.1.2' %}
                    .map(helper::absolutePos)
                    .map(pos -> helper.getLevel().getCapability(SFMWellKnownCapabilities.FLUID_HANDLER.capabilityKind(), pos, Direction.DOWN))
{% endcase %}
                    .peek(Objects::requireNonNull)
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
                    .map(x -> x.getFluidInTank(0))
                    .mapToInt(FluidStack::getAmount)
{% when '26.1.2' %}
                    .map(x -> x.getAmountAsInt(0))
                    .mapToInt(value -> value)
{% endcase %}
                    .sum();
            helper.assertTrue(found == 1000 * 25 * 24, "Not all fluids were moved (found " + found + ")");


        });
    }
}
