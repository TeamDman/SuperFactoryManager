package ca.teamdman.sfm.gametest.tests.compat.computercraft;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfm.common.item.LabelGunItem;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import dan200.computercraft.api.turtle.ITurtleUpgrade;
import dan200.computercraft.api.turtle.TurtleSide;
import dan200.computercraft.core.computer.ComputerSide;
import dan200.computercraft.shared.Registry;
import dan200.computercraft.shared.TurtleUpgrades;
import dan200.computercraft.shared.computer.core.ServerComputer;
import dan200.computercraft.shared.turtle.blocks.TileTurtle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Exercises the real CC:Tweaked turtle upgrade, command queue, and selected inventory gun. */
@SFMGameTest
public class ComputerCraftTurtleLabelerGameTest extends SFMGameTestDefinition {
    @Override
    public String template() {

        return "7x4x3";
    }

    @Override
    public int maxTicks() {

        return 300;
    }

    @Override
    public void run(SFMGameTestHelper helper) {

        BlockPos turtlePos = new BlockPos(1, 2, 1);
        BlockPos firstFurnace = new BlockPos(2, 2, 1);
        BlockPos secondFurnace = new BlockPos(2, 2, 2);
        BlockPos skippedFurnace = new BlockPos(2, 2, 0);
        BlockPos managerPos = new BlockPos(1, 3, 1);
        helper.setBlock(
                turtlePos,
                Registry.ModBlocks.TURTLE_NORMAL.get().defaultBlockState()
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST)
        );
        helper.setBlock(firstFurnace, Blocks.FURNACE);
        helper.setBlock(secondFurnace, Blocks.FURNACE);
        helper.setBlock(skippedFurnace, Blocks.FURNACE);
        helper.setBlock(new BlockPos(2, 1, 1), SFMBlocks.CABLE.get());
        helper.setBlock(new BlockPos(2, 1, 2), SFMBlocks.CABLE.get());
        helper.setBlock(new BlockPos(3, 1, 1), SFMBlocks.CABLE.get());
        helper.setBlock(new BlockPos(3, 1, 2), SFMBlocks.CABLE.get());
        helper.setBlock(managerPos, SFMBlocks.MANAGER.get());

        ItemStack blankGun = new ItemStack(SFMItems.LABEL_GUN.get());
        ITurtleUpgrade upgrade = findTurtleUpgrade(blankGun);
        helper.assertTrue(
                upgrade != null && upgrade.getUpgradeID().equals(new ResourceLocation("sfm", "labeler")),
                "The blank SFM label gun was not registered as the turtle labeler upgrade"
        );
        ItemStack nonBlankGun = new ItemStack(SFMItems.LABEL_GUN.get());
        LabelGunItem.setActiveLabel(nonBlankGun, "non_blank");
        helper.assertTrue(
                !hasTurtleUpgrade(nonBlankGun),
                "A label gun carrying state was incorrectly accepted for turtle equip"
        );

        TileTurtle turtle = helper.getBlockEntity(turtlePos, TileTurtle.class);
        turtle.getAccess().setUpgrade(TurtleSide.LEFT, upgrade);
        ItemStack runtimeGun = new ItemStack(SFMItems.LABEL_GUN.get());
        ItemStack runtimeDisk = new ItemStack(SFMItems.DISK.get());
        turtle.setItem(0, runtimeGun);
        turtle.setItem(1, runtimeDisk);
        turtle.getAccess().setSelectedSlot(0);

        ManagerBlockEntity manager = helper.getBlockEntity(managerPos, ManagerBlockEntity.class);
        ItemStack managerDisk = new ItemStack(SFMItems.DISK.get());
        DiskItem.setProgram(managerDisk, "NAME \"turtle labels\"");
        manager.setItem(0, managerDisk);
        manager.rebuildProgramAndUpdateDisk();

        ServerComputer computer = turtle.createServerComputer();
        BlockPos firstFurnaceAbsolute = helper.absolutePos(firstFurnace);
        BlockPos secondFurnaceAbsolute = helper.absolutePos(secondFurnace);
        BlockPos skippedFurnaceAbsolute = helper.absolutePos(skippedFurnace);
        ComputerCraftLuaNetworkPeripheralGameTest.writeStartupProgram(helper, computer, """
                local sfm = assert(peripheral.wrap("left"), "SFM upgrade peripheral missing")
                assert(peripheral.getType("left") == "sfm", "unexpected turtle peripheral type")
                assert(sfm.labelGun == nil and sfm.disk == nil, "SFM item API was not flattened")

                local discovery = sfm.discover("front", { contiguous = true })
                local positions = discovery.positions()
                local skipped = discovery.skippedPositions()
                assert(positions.count() == 2, "contiguous discovery did not find both cable-adjacent furnaces")
                assert(positions.contains(%d, %d, %d), "discovery omitted the first furnace")
                assert(positions.contains(%d, %d, %d), "discovery omitted the second furnace")
                assert(skipped.count() == 1, "discovery did not report the furnace without a cable neighbour")
                assert(skipped.contains(%d, %d, %d), "unexpected skipped discovery position")
                local positionSet = positions.toTable()

                local labels = sfm.labels()
                assert(labels.addAll("contiguous", positionSet))
                assert(labels.save())
                assert(labels.contains("contiguous", %d, %d, %d), "first contiguous furnace was not labelled")
                assert(labels.contains("contiguous", %d, %d, %d), "second contiguous furnace was not labelled")
                assert(labels.removeAll("contiguous", positionSet))
                assert(labels.save())
                assert(not labels.contains("contiguous", %d, %d, %d), "bulk remove did not remove first furnace label")
                assert(not labels.contains("contiguous", %d, %d, %d), "bulk remove did not remove second furnace label")

                turtle.select(2)
                assert(sfm.setProgram('NAME "turtle disk"'))
                assert(sfm.getProgram() == 'NAME "turtle disk"')
                local diskLabels = sfm.labels()
                assert(diskLabels.addAll("discovered", positionSet))
                assert(diskLabels.save())
                turtle.select(1)

                assert(labels.add("alpha", %d, %d, %d))
                assert(labels.add("beta", %d, %d, %d))
                assert(labels.save())
                assert(sfm.setActiveLabel("alpha"))
                assert(sfm.setViewMode("show_only_targeted_block"))
                assert(sfm.getViewMode() == "show_only_targeted_block", "view mode was not saved")
                assert(sfm.pick("front", false))
                assert(sfm.getActiveLabel() == "beta", "pick did not cycle target labels")
                assert(sfm.clearAll("front", false))
                labels = sfm.labels()
                assert(not labels.contains("alpha", %d, %d, %d), "clear-all did not remove alpha")
                assert(not labels.contains("beta", %d, %d, %d), "clear-all did not remove beta")

                assert(labels.add("pushed", 6, 6, 6))
                assert(labels.save())
                assert(sfm.push("up"), "push to manager failed")
                os.pullEvent("sfm_continue")
                assert(sfm.pull("up"), "pull from manager failed")
                labels = sfm.labels()
                assert(labels.contains("pulled", 7, 7, 7), "pull did not copy manager labels into turtle gun")
                redstone.setOutput("top", true)
                """.formatted(
                firstFurnaceAbsolute.getX(), firstFurnaceAbsolute.getY(), firstFurnaceAbsolute.getZ(),
                secondFurnaceAbsolute.getX(), secondFurnaceAbsolute.getY(), secondFurnaceAbsolute.getZ(),
                skippedFurnaceAbsolute.getX(), skippedFurnaceAbsolute.getY(), skippedFurnaceAbsolute.getZ(),
                firstFurnaceAbsolute.getX(), firstFurnaceAbsolute.getY(), firstFurnaceAbsolute.getZ(),
                secondFurnaceAbsolute.getX(), secondFurnaceAbsolute.getY(), secondFurnaceAbsolute.getZ(),
                firstFurnaceAbsolute.getX(), firstFurnaceAbsolute.getY(), firstFurnaceAbsolute.getZ(),
                secondFurnaceAbsolute.getX(), secondFurnaceAbsolute.getY(), secondFurnaceAbsolute.getZ(),
                firstFurnaceAbsolute.getX(), firstFurnaceAbsolute.getY(), firstFurnaceAbsolute.getZ(),
                firstFurnaceAbsolute.getX(), firstFurnaceAbsolute.getY(), firstFurnaceAbsolute.getZ(),
                firstFurnaceAbsolute.getX(), firstFurnaceAbsolute.getY(), firstFurnaceAbsolute.getZ(),
                firstFurnaceAbsolute.getX(), firstFurnaceAbsolute.getY(), firstFurnaceAbsolute.getZ()
        ));
        turtle.updateInputsImmediately();
        computer.turnOn();

        helper.runAfterDelay(120, () -> {
            helper.assertTrue(
                    LabelPositionHolder.from(managerDisk).contains("pushed", new BlockPos(6, 6, 6)),
                    "Turtle label-gun push did not update the manager disk:\n" +
                            ComputerCraftLuaNetworkPeripheralGameTest.terminalContents(computer)
            );
            LabelPositionHolder.from(managerDisk).add("pulled", new BlockPos(7, 7, 7)).save(managerDisk);
            manager.rebuildProgramAndUpdateDisk();
            computer.queueEvent("sfm_continue", null);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(
                    computer.getRedstoneOutput(ComputerSide.TOP) == 15,
                    "Turtle labeler Lua program did not complete:\n" +
                            ComputerCraftLuaNetworkPeripheralGameTest.terminalContents(computer)
            );
            helper.assertTrue(
                    LabelPositionHolder.from(runtimeGun).contains("pulled", new BlockPos(7, 7, 7)),
                    "Turtle pull did not persist onto the selected inventory gun"
            );
            helper.assertTrue(
                    "NAME \"turtle disk\"".equals(DiskItem.getProgramStringReadOnly(runtimeDisk)),
                    "Flat SFM peripheral did not persist the selected disk program"
            );
            helper.assertTrue(
                    LabelPositionHolder.from(runtimeDisk).contains("discovered", firstFurnaceAbsolute)
                            && LabelPositionHolder.from(runtimeDisk).contains("discovered", secondFurnaceAbsolute),
                    "Bulk-discovered positions were not persisted to the selected disk"
            );
            helper.succeed();
        });
    }

    @MCVersionDependentBehaviour
    private static ITurtleUpgrade findTurtleUpgrade(ItemStack itemStack) {
        return TurtleUpgrades.instance().get(itemStack);
    }

    @MCVersionDependentBehaviour
    private static boolean hasTurtleUpgrade(ItemStack itemStack) {
        return TurtleUpgrades.instance().get(itemStack) != null;
    }

}
