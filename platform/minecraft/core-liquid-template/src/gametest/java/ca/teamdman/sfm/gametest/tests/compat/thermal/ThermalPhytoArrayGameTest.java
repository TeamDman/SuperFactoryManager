package ca.teamdman.sfm.gametest.tests.compat.thermal;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import ca.teamdman.sfm.common.capability.SFMWellKnownCapabilities;
{% when '26.1.2' %}
{% endcase %}
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.registry.SFMWellKnownRegistries;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.common.registry.registration.SFMResourceTypes;
import ca.teamdman.sfm.common.util.SFMResourceLocation;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import cofh.thermal.expansion.block.entity.machine.MachineInsolatorTile;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
{% when '26.1.2' %}
import mekanism.api.Action;
import mekanism.api.AutomationType;
{% endcase %}
import mekanism.common.registries.MekanismBlocks;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
{% when '26.1.2' %}
import mekanism.common.tile.TileEntityEnergyCube;
{% endcase %}
import mekanism.common.tile.TileEntityFluidTank;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import net.minecraftforge.fluids.FluidStack;
{% when '26.1.2' %}
import net.neoforged.neoforge.fluids.FluidStack;
{% endcase %}

import java.util.ArrayList;




/**
 * Migrated from SFMThermalCompatGameTests.thermal_phyto_array
 */
@SuppressWarnings({
        "RedundantSuppression",
        "DataFlowIssue",
        "OptionalGetWithoutIsPresent",
        "DuplicatedCode",
        "ArraysAsListWithZeroOrOneArgument"
})
@SFMGameTest
public class ThermalPhytoArrayGameTest extends SFMGameTestDefinition {

    @Override
    public String template() {
        return "25x3x25";
    }

    @Override
    public int maxTicks() {
        return 20 * 20;
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        // designate positions
        var phytoPositions = new ArrayList<BlockPos>();
        var resultChestPositions = new ArrayList<BlockPos>();
        var seedChestPositions = new ArrayList<BlockPos>();
        var managerPos = new BlockPos(0, 2, 0);
        var powerPos = new BlockPos(1, 2, 0);
        var waterPos = new BlockPos(2, 2, 0);

        // set up power
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        helper.setBlock(powerPos, MekanismBlocks.ULTIMATE_ENERGY_CUBE.getBlock());
{% when '26.1.2' %}
        helper.setBlock(powerPos, MekanismBlocks.ULTIMATE_ENERGY_CUBE.get());
{% endcase %}
        helper
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
                .getBlockEntity(powerPos)
                .getCapability(SFMWellKnownCapabilities.ENERGY.capabilityKind(), Direction.UP)
                .ifPresent(energy -> energy.receiveEnergy(Integer.MAX_VALUE, false));
{% when '26.1.2' %}
                .getBlockEntity(powerPos, TileEntityEnergyCube.class)
                .getEnergyContainer()
                .insert(Integer.MAX_VALUE, Action.EXECUTE, AutomationType.INTERNAL);
{% endcase %}

        // set up water
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        helper.setBlock(waterPos, MekanismBlocks.CREATIVE_FLUID_TANK.getBlock());
{% when '26.1.2' %}
        helper.setBlock(waterPos, MekanismBlocks.CREATIVE_FLUID_TANK.get());
{% endcase %}
        TileEntityFluidTank tank = helper.getBlockEntity(waterPos, TileEntityFluidTank.class);
        tank.setFluidInTank(0, new FluidStack(Fluids.WATER, Integer.MAX_VALUE));

        // set up phytos
        var phytoBlock = SFMWellKnownRegistries.BLOCKS.get(SFMResourceLocation.fromNamespaceAndPath("thermal", "machine_insolator"));
        SFM.LOGGER.debug("Setting up phytos with block {}", phytoBlock);
        for (int x = 0; x < 25; x++) {
            for (int z = 1; z < 25; z++) {
                BlockPos cableBelow = new BlockPos(x, 2, z);
                helper.setBlock(cableBelow, SFMBlocks.CABLE.get());
                BlockPos phytoPos = new BlockPos(x, 3, z);
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
                helper.setBlock(phytoPos, phytoBlock);
{% when '26.1.2' %}
                helper.setBlock(phytoPos, phytoBlock.get().value());
{% endcase %}
                phytoPositions.add(phytoPos);
                var phyto = helper.getBlockEntity(phytoPos, MachineInsolatorTile.class);
                phyto.setSideConfig(Direction.UP, MachineInsolatorTile.SideConfig.SIDE_INPUT);
                phyto.setSideConfig(Direction.DOWN, MachineInsolatorTile.SideConfig.SIDE_OUTPUT);
            }
        }

        // set up destinations
        SFM.LOGGER.debug("Setting up destinations");
        for (int i = 3; i <= 4; i++) {
            BlockPos pos = new BlockPos(i, 2, 0);
            helper.setBlock(pos, SFMBlocks.TEST_BARREL.get());
            resultChestPositions.add(pos);
        }

        // set up ingredients
        //noinspection NonStrictComparisonCanBeEquality
        for (int i = 6; i <= 6; i++) {
            BlockPos pos = new BlockPos(i, 2, 0);
            helper.setBlock(pos, SFMBlocks.TEST_BARREL.get());
            seedChestPositions.add(pos);
            var items = new Item[]{
                    Items.BEETROOT_SEEDS,
                    Items.MELON_SEEDS,
                    Items.PUMPKIN_SEEDS,
                    Items.WHEAT_SEEDS,
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
                    SFMResourceTypes.ITEM.get().getItemFromRegistryKey(SFMResourceLocation.fromNamespaceAndPath("thermal", "phytogro")),
                    SFMResourceTypes.ITEM.get().getItemFromRegistryKey(SFMResourceLocation.fromNamespaceAndPath("thermal", "phytogro")),
                    SFMResourceTypes.ITEM.get().getItemFromRegistryKey(SFMResourceLocation.fromNamespaceAndPath("thermal", "phytogro")),
                    SFMResourceTypes.ITEM.get().getItemFromRegistryKey(SFMResourceLocation.fromNamespaceAndPath("thermal", "phytogro")),
                    SFMResourceTypes.ITEM.get().getItemFromRegistryKey(SFMResourceLocation.fromNamespaceAndPath("thermal", "phytogro")),
{% when '26.1.2' %}
                    SFMResourceTypes.ITEM.get().getItemFromRegistryKey(SFMResourceLocation.fromNamespaceAndPath("thermal", "phytogro")).value(),
                    SFMResourceTypes.ITEM.get().getItemFromRegistryKey(SFMResourceLocation.fromNamespaceAndPath("thermal", "phytogro")).value(),
                    SFMResourceTypes.ITEM.get().getItemFromRegistryKey(SFMResourceLocation.fromNamespaceAndPath("thermal", "phytogro")).value(),
                    SFMResourceTypes.ITEM.get().getItemFromRegistryKey(SFMResourceLocation.fromNamespaceAndPath("thermal", "phytogro")).value(),
                    SFMResourceTypes.ITEM.get().getItemFromRegistryKey(SFMResourceLocation.fromNamespaceAndPath("thermal", "phytogro")).value(),
{% endcase %}
                    };
            int slot = 0;
            for (Item item : items) {
                helper.getItemHandler(pos).insertItem(slot++, new ItemStack(item, 64), false);
            }
        }

        // set up the manager
        helper.setBlock(managerPos, SFMBlocks.MANAGER.get());
        ManagerBlockEntity manager = helper.getBlockEntity(managerPos, ManagerBlockEntity.class);
        manager.setItem(0, new ItemStack(SFMItems.DISK.get()));

        // create the program
        var program = """
                    NAME "thermal phyto array test"
                    
                    EVERY TICK DO
                        INPUT forge_energy:forge:energy FROM power NORTH SIDE
                        OUTPUT forge_energy:forge:energy TO phytos
                    END
                    
                    EVERY 20 TICKS DO
                        INPUT *seed* FROM seeds
                        OUTPUT RETAIN 2 EACH *seed* TO phytos TOP SIDE SLOTS 0
                        OUTPUT RETAIN 2 *seed* TO EACH phytos TOP SIDE SLOTS 0
                    FORGET
                        INPUT FROM seeds
                        OUTPUT RETAIN 2 phytogro TO EACH phytos TOP SIDE SLOTS 1
                    FORGET
                        INPUT fluid:: FROM water TOP SIDE
                        OUTPUT fluid:: TO phytos TOP SIDE
                    FORGET
                        INPUT FROM phytos BOTTOM SIDE
                        OUTPUT TO results TOP SIDE
                    FORGET
                        INPUT forge_energy:forge:energy FROM power NORTH SIDE
                        OUTPUT forge_energy:forge:energy TO phytos
                    END
                """.stripTrailing().stripIndent();

        // set the labels
        LabelPositionHolder.empty()
                .addAll("phytos", phytoPositions.stream().map(helper::absolutePos).toList())
                .addAll("seeds", seedChestPositions.stream().map(helper::absolutePos).toList())
                .addAll("results", resultChestPositions.stream().map(helper::absolutePos).toList())
                .add("power", helper.absolutePos(powerPos))
                .add("water", helper.absolutePos(waterPos))
                .save(manager.getDisk());

        // load the program
        manager.setProgram(program.stripIndent());
        helper.succeedIfManagerDidThingWithoutLagging(manager, () -> {
            SFM.LOGGER.warn("TODO: finish implementing thermal_phyto_array test");
        });
    }
}
