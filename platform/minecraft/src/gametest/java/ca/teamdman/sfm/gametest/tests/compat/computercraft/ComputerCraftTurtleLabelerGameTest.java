package ca.teamdman.sfm.gametest.tests.compat.computercraft;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfm.common.item.LabelGunItem;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
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
        BlockPos managerPos = new BlockPos(1, 3, 1);
        helper.setBlock(
                turtlePos,
                Registry.ModBlocks.TURTLE_NORMAL.get().defaultBlockState()
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST)
        );
        helper.setBlock(firstFurnace, Blocks.FURNACE);
        helper.setBlock(secondFurnace, Blocks.FURNACE);
        helper.setBlock(new BlockPos(2, 1, 1), SFMBlocks.CABLE.get());
        helper.setBlock(new BlockPos(2, 1, 2), SFMBlocks.CABLE.get());
        helper.setBlock(new BlockPos(3, 1, 1), SFMBlocks.CABLE.get());
        helper.setBlock(new BlockPos(3, 1, 2), SFMBlocks.CABLE.get());
        helper.setBlock(managerPos, SFMBlocks.MANAGER.get());

        ItemStack blankGun = new ItemStack(SFMItems.LABEL_GUN.get());
        ITurtleUpgrade upgrade = TurtleUpgrades.instance().get(blankGun);
        helper.assertTrue(
                upgrade != null && upgrade.getUpgradeID().equals(new ResourceLocation("sfm", "labeler")),
                "The blank SFM label gun was not registered as the turtle labeler upgrade"
        );
        ItemStack nonBlankGun = new ItemStack(SFMItems.LABEL_GUN.get());
        LabelGunItem.setActiveLabel(nonBlankGun, "non_blank");
        helper.assertTrue(
                TurtleUpgrades.instance().get(nonBlankGun) == null,
                "A label gun carrying state was incorrectly accepted for turtle equip"
        );

        TileTurtle turtle = helper.getBlockEntity(turtlePos, TileTurtle.class);
        turtle.getAccess().setUpgrade(TurtleSide.LEFT, upgrade);
        ItemStack runtimeGun = new ItemStack(SFMItems.LABEL_GUN.get());
        turtle.setItem(0, runtimeGun);
        turtle.getAccess().setSelectedSlot(0);

        ManagerBlockEntity manager = helper.getBlockEntity(managerPos, ManagerBlockEntity.class);
        ItemStack managerDisk = new ItemStack(SFMItems.DISK.get());
        DiskItem.setProgram(managerDisk, "NAME \"turtle labels\"");
        manager.setItem(0, managerDisk);
        manager.rebuildProgramAndUpdateDisk();

        ServerComputer computer = turtle.createServerComputer();
        BlockPos firstFurnaceAbsolute = helper.absolutePos(firstFurnace);
        BlockPos secondFurnaceAbsolute = helper.absolutePos(secondFurnace);
        ComputerCraftLuaNetworkPeripheralGameTest.writeStartupProgram(helper, computer, """
                local labeler = assert(peripheral.wrap("left"), "labeler upgrade peripheral missing")
                assert(peripheral.getType("left") == "sfm_labeler", "unexpected turtle peripheral type")
                local gun = assert(labeler.labelGun(), "selected turtle slot did not expose label gun")

                assert(gun.setActiveLabel("contiguous"))
                assert(labeler.toggle("front", true))
                local labels = gun.labels()
                assert(labels.contains("contiguous", %d, %d, %d), "first contiguous furnace was not labelled")
                assert(labels.contains("contiguous", %d, %d, %d), "second contiguous furnace was not labelled")
                assert(labeler.clearActive("front", true))
                labels = gun.labels()
                assert(not labels.contains("contiguous", %d, %d, %d), "contiguous clear-active did not remove first furnace label")
                assert(not labels.contains("contiguous", %d, %d, %d), "contiguous clear-active did not remove second furnace label")

                assert(labels.add("alpha", %d, %d, %d))
                assert(labels.add("beta", %d, %d, %d))
                assert(labels.save())
                assert(gun.setActiveLabel("alpha"))
                assert(labeler.pick("front", false))
                assert(gun.getActiveLabel() == "beta", "pick did not cycle target labels")
                assert(labeler.clearAll("front", false))
                labels = gun.labels()
                assert(not labels.contains("alpha", %d, %d, %d), "clear-all did not remove alpha")
                assert(not labels.contains("beta", %d, %d, %d), "clear-all did not remove beta")

                assert(labels.add("pushed", 6, 6, 6))
                assert(labels.save())
                assert(labeler.push("up"), "push to manager failed")
                os.pullEvent("sfm_continue")
                assert(labeler.pull("up"), "pull from manager failed")
                labels = gun.labels()
                assert(labels.contains("pulled", 7, 7, 7), "pull did not copy manager labels into turtle gun")
                redstone.setOutput("top", true)
                """.formatted(
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
            helper.succeed();
        });
    }

}
