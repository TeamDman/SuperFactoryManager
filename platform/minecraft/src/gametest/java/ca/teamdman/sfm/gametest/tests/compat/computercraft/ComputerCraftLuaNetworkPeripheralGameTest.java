package ca.teamdman.sfm.gametest.tests.compat.computercraft;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import dan200.computercraft.api.ComputerCraftAPI;
import dan200.computercraft.api.filesystem.MountConstants;
import dan200.computercraft.api.filesystem.WritableMount;
import dan200.computercraft.core.computer.ComputerSide;
import dan200.computercraft.shared.ModRegistry;
import dan200.computercraft.shared.computer.blocks.ComputerBlockEntity;
import dan200.computercraft.shared.computer.core.ServerComputer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.IntStream;

/** Exercises mutable SFM handles through CC:Tweaked's real Lua runtime. */
@SFMGameTest
@MCVersionDependentBehaviour // CC:Tweaked 1.113.1+ internal GameTest fixture API
public class ComputerCraftLuaNetworkPeripheralGameTest extends SFMGameTestDefinition {
    @Override
    public String template() {

        return "7x4x3";
    }

    @Override
    public int maxTicks() {

        // CC:Tweaked 1.113.1 waits 50 computer ticks before launching a newly powered computer.
        return 200;
    }

    @Override
    public void run(SFMGameTestHelper helper) {

        BlockPos computerPos = new BlockPos(1, 2, 1);
        BlockPos chestPos = new BlockPos(1, 3, 1);
        BlockPos entryCablePos = new BlockPos(2, 2, 1);
        BlockPos firstManagerPos = new BlockPos(3, 2, 1);
        BlockPos secondManagerPos = new BlockPos(5, 2, 1);
        BlockPos sourcePos = new BlockPos(0, 2, 1);

        helper.setBlock(computerPos, normalComputerFacing(Direction.EAST));
        helper.setBlock(chestPos, Blocks.CHEST);
        helper.setBlock(entryCablePos, SFMBlocks.CABLE.get());
        helper.setBlock(firstManagerPos, SFMBlocks.MANAGER.get());
        helper.setBlock(new BlockPos(4, 2, 1), SFMBlocks.CABLE.get());
        helper.setBlock(secondManagerPos, SFMBlocks.MANAGER.get());

        ManagerBlockEntity firstManager = helper.getBlockEntity(firstManagerPos, ManagerBlockEntity.class);
        ItemStack managerDisk = new ItemStack(SFMItems.DISK.get());
        firstManager.setItem(0, managerDisk);
        DiskItem.setProgram(managerDisk, "NAME \"CC Lua network test\"");
        firstManager.rebuildProgramAndUpdateDisk();
        LabelPositionHolder.from(managerDisk).add("source", helper.absolutePos(sourcePos)).save(managerDisk);
        firstManager.rebuildProgramAndUpdateDisk();

        ChestBlockEntity chest = helper.getBlockEntity(chestPos, ChestBlockEntity.class);
        chest.setItem(0, managerDisk.copy());
        chest.setItem(1, new ItemStack(SFMItems.LABEL_GUN.get()));

        helper.runAfterDelay(1, () -> {
            ComputerBlockEntity computerBlockEntity = helper.getBlockEntity(computerPos, ComputerBlockEntity.class);
            ServerComputer computer = computerBlockEntity.createServerComputer();
            writeStartupProgram(helper, computer, """
                local network = assert(peripheral.wrap("front"), "SFM cable was not exposed on the computer front")
                assert(peripheral.getType("front") == "sfm_network", "unexpected peripheral type")

                local managers = network.getManagers()
                assert(managers.count() == 2, "expected two network managers")
                assert(managers[1] == nil, "manager collection was materialised as an array table")

                local manager = assert(managers.get(1), "first manager missing")
                local x, y, z = manager.position()
                assert(x == %d and y == %d and z == %d, "manager position was not readable")
                assert(manager.state() == "running", "manager state was not readable")
                local disk = assert(manager.disk(), "manager disk missing")
                assert(disk.getProgram() == 'NAME "CC Lua network test"', "disk program was not readable")
                local managerLabels = disk.labels()
                assert(managerLabels.labelCount() == 1 and managerLabels.labelName(1) == "source", "manager labels were not readable")
                local sourceX = managerLabels.position("source", 1)
                assert(sourceX == %d, "manager label position was not readable")

                local ok, status = disk.setProgram("EVERY 20 TICKS DO")
                assert(ok and status == "invalid_program", "invalid disk source was not retained with diagnostics")
                assert(disk.setProgram('NAME "CC Lua network test"'), "valid disk source could not be restored")

                local chest = assert(peripheral.wrap("top"), "chest peripheral was not available")
                assert(chest.getItemDetail(1, true).sfm == nil, "legacy sfm item detail table still exists")
                local chestDisk = assert(chest.getSfmDisk(1), "disk handle was not added to normal inventory peripheral")
                local labels = chestDisk.labels()
                for index = 1, 17 do
                    assert(labels.add("label_" .. index, index, 1, 0))
                end
                for index = 1, 65 do
                    assert(labels.add("large", index, 2, 0))
                end
                assert(labels.save(), "disk label session did not save")
                local savedLabels = chestDisk.labels()
                local savedLabelCount = savedLabels.labelCount()
                assert(savedLabelCount == 19, "large label collection was capped: " .. tostring(savedLabelCount) .. " first=" .. tostring(savedLabels.labelName(1)))
                assert(savedLabels.positionCount("large") == 65, "large position collection was capped")

                local gun = assert(chest.getSfmLabelGun(2), "label gun handle was not added to normal inventory peripheral")
                assert(gun.setActiveLabel("source"), "could not set active label")
                assert(gun.setViewMode("show_only_targeted_block"), "could not set label gun view mode")
                assert(gun.getActiveLabel() == "source", "active label was not saved")
                assert(gun.getViewMode() == "show_only_targeted_block", "view mode was not saved")

                redstone.setOutput("top", true)
                """.formatted(
                helper.absolutePos(firstManagerPos).getX(),
                helper.absolutePos(firstManagerPos).getY(),
                helper.absolutePos(firstManagerPos).getZ(),
                helper.absolutePos(sourcePos).getX()
        ));
        computerBlockEntity.updateInputsImmediately();
        computer.turnOn();

        helper.succeedWhen(() -> {
            // The headless GameTest server advances ticks much faster than wall time, while CC's Lua VM runs on its worker thread.
            LockSupport.parkNanos(1_000_000L);
            helper.assertTrue(
                    computer.getRedstoneOutput(ComputerSide.TOP) == 15,
                    "CC:Tweaked Lua program did not complete (state=" + computer.getState()
                            + ", on=" + computer.isOn() + "):\n" + terminalContents(computer)
            );
            helper.succeed();
        });
        });
    }

    static String terminalContents(ServerComputer computer) {

        var terminal = computer.getTerminalState().create();
        return IntStream.range(0, terminal.getHeight())
                .mapToObj(line -> terminal.getLine(line).toString())
                .reduce((first, second) -> first + "\n" + second)
                .orElse("<empty terminal>");
    }

    private static BlockState normalComputerFacing(Direction facing) {

        Block computer = ModRegistry.Blocks.COMPUTER_NORMAL.get();
        if (computer == null) {
            throw new IllegalStateException("CC:Tweaked normal computer block was not registered");
        }
        return computer.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
    }

    static void writeStartupProgram(
            SFMGameTestHelper helper,
            ServerComputer computer,
            String program
    ) {

        WritableMount mount = ComputerCraftAPI.createSaveDirMount(
                helper.getLevel().getServer(),
                "computer/" + computer.getID(),
                1_000_000
        );
        if (mount == null) {
            throw new IllegalStateException("CC:Tweaked did not create the computer save mount");
        }

        try (
                var channel = mount.openFile("startup.lua", MountConstants.WRITE_OPTIONS);
                var output = Channels.newOutputStream(channel)
        ) {
            output.write(program.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write CC:Tweaked test startup.lua", e);
        }
    }

}
