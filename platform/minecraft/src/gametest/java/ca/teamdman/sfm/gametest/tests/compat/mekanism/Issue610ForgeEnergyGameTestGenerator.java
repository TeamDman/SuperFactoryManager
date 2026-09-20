package ca.teamdman.sfm.gametest.tests.compat.mekanism;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.gametest.*;
import mekanism.api.math.FloatingLong;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.registries.MekanismBlocks;
import mekanism.common.tile.TileEntityEnergyCube;
import mekanism.common.util.UnitDisplayUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Real Mekanism FE capabilities, with explicitly ordered destinations for issue #610. */
@SFMGameTestGenerator
public class Issue610ForgeEnergyGameTestGenerator extends SFMGameTestGeneratorBase {
    @Override
    public void generateTests(Consumer<SFMGameTestDefinition> tests) {
        for (String gap : List.of("0", "0.1", "1", "2.4", "2.5", "2.6", "5")) {
            for (boolean nearFullFirst : List.of(true, false)) {
                tests.accept(new BoundaryTest(gap, nearFullFirst));
            }
        }
    }

    private static class BoundaryTest extends SFMGameTestDefinition {
        private final String gap;
        private final boolean nearFullFirst;

        BoundaryTest(String gap, boolean nearFullFirst) {
            this.gap = gap;
            this.nearFullFirst = nearFullFirst;
        }

        @Override public String template() { return "3x3x3"; }
        @Override public String testName() {
            return "issue_610_mekanism_fe_gap_" + gap.replace('.', '_')
                   + (nearFullFirst ? "_near_full_first" : "_empty_first");
        }

        @Override public void run(SFMGameTestHelper helper) {
            BlockPos managerPos = new BlockPos(1, 2, 1);
            BlockPos sourcePos = new BlockPos(1, 2, 0);
            BlockPos nearFullPos = new BlockPos(0, 2, 1);
            BlockPos emptyPos = new BlockPos(2, 2, 1);
            for (BlockPos pos : List.of(sourcePos, nearFullPos, emptyPos)) {
                helper.setBlock(pos, MekanismBlocks.ULTIMATE_ENERGY_CUBE.getBlock());
                helper.getBlockEntity(pos, TileEntityEnergyCube.class).getConfig()
                        .getConfig(TransmissionType.ENERGY).setEjecting(false);
            }
            helper.setBlock(managerPos, SFMBlocks.MANAGER.get());
            var manager = helper.getBlockEntity(managerPos, ManagerBlockEntity.class);
            var source = helper.getBlockEntity(sourcePos, TileEntityEnergyCube.class);
            var nearFull = helper.getBlockEntity(nearFullPos, TileEntityEnergyCube.class);
            var empty = helper.getBlockEntity(emptyPos, TileEntityEnergyCube.class);
            FloatingLong sourceBefore = UnitDisplayUtils.EnergyUnit.FORGE_ENERGY.convertFrom(100);
            FloatingLong nearFullBefore = nearFull.getMaxEnergy(0).subtract(FloatingLong.parseFloatingLong(gap));
            source.setEnergy(0, sourceBefore);
            nearFull.setEnergy(0, nearFullBefore);
            empty.setEnergy(0, FloatingLong.ZERO);
            IEnergyStorage sourceFe = source.getCapability(ForgeCapabilities.ENERGY, Direction.NORTH).resolve().orElseThrow();
            IEnergyStorage nearFullFe = nearFull.getCapability(ForgeCapabilities.ENERGY, Direction.UP).resolve().orElseThrow();
            IEnergyStorage emptyFe = empty.getCapability(ForgeCapabilities.ENERGY, Direction.UP).resolve().orElseThrow();
            int expectedNearFull = nearFullFirst ? nearFullFe.receiveEnergy(100, true) : 0;
            helper.assertTrue(sourceFe.extractEnergy(100, true) == 100, "Source must supply the complete budget");
            helper.assertTrue(emptyFe.receiveEnergy(100, true) == 100, "Empty destination must accept the budget");
            helper.assertTrue(source.getEnergy(0).equals(sourceBefore) && nearFull.getEnergy(0).equals(nearFullBefore),
                    "Capability simulation changed stored energy");
            manager.setItem(0, new ItemStack(SFMItems.DISK.get()));
            LabelPositionHolder.empty()
                    .add("source", helper.absolutePos(sourcePos))
                    .add("near_full", helper.absolutePos(nearFullPos))
                    .add("hungry", helper.absolutePos(emptyPos))
                    .save(Objects.requireNonNull(manager.getDisk()));
            // Separate label names preserve order; no reliance on hash iteration of a shared label.
            manager.setProgram("EVERY TICK DO\nINPUT 100 fe:: FROM source NORTH SIDE\nOUTPUT fe:: TO "
                               + (nearFullFirst ? "near_full, hungry" : "hungry, near_full") + " TOP SIDE\nEND");
            helper.succeedIfManagerDidThingWithoutLagging(manager, () -> {
                helper.assertTrue(source.getEnergy(0).isZero(), "Source was not drained; gap=" + gap);
                helper.assertTrue(emptyFe.getEnergyStored() == 100 - expectedNearFull,
                        "Later destination starved; gap=" + gap + ", expected=" + (100 - expectedNearFull)
                        + ", actual=" + emptyFe.getEnergyStored());
                FloatingLong transferred = nearFull.getEnergy(0).subtract(nearFullBefore).add(empty.getEnergy(0));
                helper.assertTrue(transferred.equals(sourceBefore), "Energy was lost or duplicated: " + transferred);
            });
        }
    }
}
