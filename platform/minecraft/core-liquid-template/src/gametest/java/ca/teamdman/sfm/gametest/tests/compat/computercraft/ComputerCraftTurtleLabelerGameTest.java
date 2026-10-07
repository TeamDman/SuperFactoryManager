package ca.teamdman.sfm.gametest.tests.compat.computercraft;

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.20', '1.21.1', '26.1.2' %}
{% when '1.21' %}
import ca.teamdman.sfm.common.util.SFMResourceLocation;

{% endcase %}
import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfm.common.item.LabelGunItem;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.20', '1.21' %}
{% when '1.21.1', '26.1.2' %}
import ca.teamdman.sfm.common.compat.computercraft.SFMLabelerTurtleUpgrade;
{% endcase %}
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import dan200.computercraft.api.turtle.ITurtleUpgrade;
import dan200.computercraft.api.turtle.TurtleSide;
import dan200.computercraft.core.computer.ComputerSide;
{% case minecraft_version %}
{% when '1.19.2' %}
import dan200.computercraft.shared.Registry;
import dan200.computercraft.shared.TurtleUpgrades;
{% when '1.19.4', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.20', '1.21', '1.21.1', '26.1.2' %}
import dan200.computercraft.impl.TurtleUpgrades;
import dan200.computercraft.shared.ModRegistry;
{% endcase %}
import dan200.computercraft.shared.computer.core.ServerComputer;
{% case minecraft_version %}
{% when '1.19.2' %}
import dan200.computercraft.shared.turtle.blocks.TileTurtle;
{% when '1.19.4', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.20', '1.21', '1.21.1', '26.1.2' %}
import dan200.computercraft.shared.turtle.blocks.TurtleBlockEntity;
{% endcase %}
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.20', '1.21' %}
import net.minecraft.resources.ResourceLocation;
{% when '1.21.1', '26.1.2' %}
{% endcase %}
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

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.20', '1.21' %}
        return 300;
{% when '1.21.1', '26.1.2' %}
        return 500;
{% endcase %}
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
{% case minecraft_version %}
{% when '1.19.2' %}
                Registry.ModBlocks.TURTLE_NORMAL.get().defaultBlockState()
{% when '1.19.4', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.20', '1.21', '1.21.1', '26.1.2' %}
                ModRegistry.Blocks.TURTLE_NORMAL.get().defaultBlockState()
{% endcase %}
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
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.20', '1.21' %}
        ITurtleUpgrade upgrade = findTurtleUpgrade(blankGun);
{% when '1.21.1', '26.1.2' %}
        TurtleBlockEntity turtle = helper.getBlockEntity(turtlePos, TurtleBlockEntity.class);
        ITurtleUpgrade upgrade = equipTurtleUpgrade(helper, turtle, blankGun);
{% endcase %}
        helper.assertTrue(
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.20' %}
                upgrade != null && upgrade.getUpgradeID().equals(new ResourceLocation("sfm", "labeler")),
{% when '1.21' %}
                upgrade != null && upgrade.getUpgradeID().equals(SFMResourceLocation.fromNamespaceAndPath("sfm", "labeler")),
{% when '1.21.1', '26.1.2' %}
                upgrade instanceof SFMLabelerTurtleUpgrade,
{% endcase %}
                "The blank SFM label gun was not registered as the turtle labeler upgrade"
        );
        ItemStack nonBlankGun = new ItemStack(SFMItems.LABEL_GUN.get());
        LabelGunItem.setActiveLabel(nonBlankGun, "non_blank");
        helper.assertTrue(
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.20', '1.21' %}
                !hasTurtleUpgrade(nonBlankGun),
{% when '1.21.1', '26.1.2' %}
                !hasTurtleUpgrade(helper, nonBlankGun),
{% endcase %}
                "A label gun carrying state was incorrectly accepted for turtle equip"
        );

{% case minecraft_version %}
{% when '1.19.2' %}
        TileTurtle turtle = helper.getBlockEntity(turtlePos, TileTurtle.class);
        turtle.getAccess().setUpgrade(TurtleSide.LEFT, upgrade);
{% when '1.19.4', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.20', '1.21' %}
        TurtleBlockEntity turtle = helper.getBlockEntity(turtlePos, TurtleBlockEntity.class);
        turtle.getAccess().setUpgrade(TurtleSide.LEFT, upgrade);
{% when '1.21.1', '26.1.2' %}
{% endcase %}
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

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.20', '1.21' %}
        helper.runAfterDelay(120, () -> {
{% when '1.21.1', '26.1.2' %}
        helper.runAfterDelay(250, () -> {
{% endcase %}
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
{% case minecraft_version %}
{% when '1.19.2', '1.20' %}
    private static ITurtleUpgrade findTurtleUpgrade(ItemStack itemStack) {
        return TurtleUpgrades.instance().get(itemStack);
{% when '1.19.4', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21' %}
    private static ITurtleUpgrade findTurtleUpgrade(ItemStack itemStack) {
        var upgradeData = TurtleUpgrades.instance().get(itemStack);
        return upgradeData == null ? null : upgradeData.upgrade();
{% when '1.21.1', '26.1.2' %}
    private static ITurtleUpgrade equipTurtleUpgrade(
            SFMGameTestHelper helper,
            TurtleBlockEntity turtle,
            ItemStack itemStack
    ) {

        var upgradeData = TurtleUpgrades.instance().get(helper.getLevel().registryAccess(), itemStack);
        turtle.getAccess().setUpgrade(TurtleSide.LEFT, upgradeData);
        return upgradeData == null ? null : upgradeData.upgrade();
{% endcase %}
    }

    @MCVersionDependentBehaviour
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.20', '1.21' %}
    private static boolean hasTurtleUpgrade(ItemStack itemStack) {
        return TurtleUpgrades.instance().get(itemStack) != null;
{% when '1.21.1', '26.1.2' %}
    private static boolean hasTurtleUpgrade(SFMGameTestHelper helper, ItemStack itemStack) {
        return TurtleUpgrades.instance().get(helper.getLevel().registryAccess(), itemStack) != null;
{% endcase %}
    }

}
