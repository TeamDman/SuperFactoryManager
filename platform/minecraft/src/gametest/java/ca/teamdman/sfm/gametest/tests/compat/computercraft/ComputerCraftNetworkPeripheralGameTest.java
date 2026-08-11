package ca.teamdman.sfm.gametest.tests.compat.computercraft;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.compat.computercraft.SFMDiskHandle;
import ca.teamdman.sfm.common.compat.computercraft.SFMLabelPositionHolderHandle;
import ca.teamdman.sfm.common.compat.computercraft.SFMManagerCollectionHandle;
import ca.teamdman.sfm.common.compat.computercraft.SFMManagerHandle;
import ca.teamdman.sfm.common.compat.computercraft.SFMNetworkPeripheral;
import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import dan200.computercraft.api.peripheral.PeripheralCapability;
import java.util.Objects;
import net.minecraft.world.level.block.Blocks;

@SFMGameTest
public class ComputerCraftNetworkPeripheralGameTest extends SFMGameTestDefinition {
    @Override
    public String template() {

        return "6x3x3";
    }

    @Override
    public void run(SFMGameTestHelper helper) {

        BlockPos firstManagerPos = new BlockPos(1, 2, 0);
        BlockPos cablePos = new BlockPos(2, 2, 0);
        BlockPos bridgeCablePos = new BlockPos(3, 2, 0);
        BlockPos secondManagerPos = new BlockPos(4, 2, 0);
        BlockPos managerlessCablePos = new BlockPos(0, 2, 2);
        helper.setBlock(firstManagerPos, SFMBlocks.MANAGER.get());
        helper.setBlock(cablePos, SFMBlocks.CABLE.get());
        helper.setBlock(bridgeCablePos, SFMBlocks.CABLE.get());
        helper.setBlock(secondManagerPos, SFMBlocks.MANAGER.get());
        helper.setBlock(managerlessCablePos, SFMBlocks.CABLE.get());

        ManagerBlockEntity firstManager = helper.getBlockEntity(firstManagerPos, ManagerBlockEntity.class);
        ItemStack firstDisk = new ItemStack(SFMItems.DISK.get());
        firstManager.setItem(0, firstDisk);
        firstManager.setProgram("NAME \"CC network test\"");
        LabelPositionHolder.from(firstDisk).add("source", helper.absolutePos(new BlockPos(0, 2, 0))).save(firstDisk);

        ManagerBlockEntity secondManager = helper.getBlockEntity(secondManagerPos, ManagerBlockEntity.class);
        ItemStack secondDisk = new ItemStack(SFMItems.DISK.get());
        DiskItem.setProgram(secondDisk, "NAME \"CC stale manager test\"");
        secondManager.setItem(0, secondDisk);
        secondManager.rebuildProgramAndUpdateDisk();

        SFMNetworkPeripheral peripheral = (SFMNetworkPeripheral) Objects.requireNonNull(
                helper.getLevel().getCapability(
                        PeripheralCapability.get(),
                        helper.absolutePos(cablePos),
                        Direction.NORTH
                ),
                "SFM cable did not expose an SFM network peripheral"
        );
        helper.assertTrue(
                SFMNetworkPeripheral.TYPE.equals(peripheral.getType()),
                "SFM cable peripheral reported an unexpected type: " + peripheral.getType()
        );

        SFMManagerCollectionHandle managers = peripheral.getManagers();
        helper.assertTrue(managers.count() == 2, "Network manager collection did not enumerate both managers");
        SFMManagerHandle first = managers.get(1);
        helper.assertTrue(first != null, "First manager handle was missing");
        Object[] position = first.position();
        helper.assertTrue(
                Integer.valueOf(helper.absolutePos(firstManagerPos).getX()).equals(position[0]),
                "Manager handle did not expose a deterministic position"
        );
        helper.assertTrue("running".equals(first.state()[0]), "Manager handle did not expose state");

        SFMDiskHandle disk = first.disk();
        helper.assertTrue(disk != null, "Manager handle did not acquire its disk");
        helper.assertTrue(
                "NAME \"CC network test\"".equals(disk.getProgram()[0]),
                "Disk handle did not expose source"
        );
        SFMLabelPositionHolderHandle labels = disk.labels();
        helper.assertTrue(labels != null, "Disk labels were not a handle");
        helper.assertTrue(labels.labelCount() == 1 && "source".equals(labels.labelName(1)), "Disk labels were unreadable");

        var directManagerPeripheral = Objects.requireNonNull(
                helper.getLevel().getCapability(
                        PeripheralCapability.get(),
                        helper.absolutePos(firstManagerPos),
                        Direction.NORTH
                ),
                "SFM manager did not expose an SFM network peripheral"
        );
        helper.assertTrue(
                ((SFMNetworkPeripheral) directManagerPeripheral).getManagers().count() == 2,
                "An SFM manager did not expose the network reached through its own cable membership"
        );

        var managerlessPeripheral = Objects.requireNonNull(
                helper.getLevel().getCapability(
                        PeripheralCapability.get(),
                        helper.absolutePos(managerlessCablePos),
                        Direction.NORTH
                ),
                "Managerless SFM cable did not expose an SFM network peripheral"
        );
        helper.assertTrue(
                ((SFMNetworkPeripheral) managerlessPeripheral).getManagers().count() == 0,
                "A managerless SFM cable did not expose the documented empty network view"
        );

        SFMManagerHandle second = managers.get(2);
        SFMDiskHandle staleDisk = second.disk();
        helper.setBlock(bridgeCablePos, Blocks.AIR);
        helper.assertTrue(peripheral.getManagers().count() == 1, "Peripheral did not re-resolve after a split");
        Object[] staleResult = staleDisk.setProgram("NAME \"should not save\"");
        helper.assertTrue(
                Boolean.FALSE.equals(staleResult[0]) && "manager_unreachable".equals(staleResult[1]),
                "A split manager disk handle did not reject mutation"
        );

        helper.setBlock(bridgeCablePos, SFMBlocks.CABLE.get());
        helper.assertTrue(peripheral.getManagers().count() == 2, "Peripheral did not re-resolve after a rejoin");
        helper.setBlock(cablePos, Blocks.AIR);
        helper.assertTrue(peripheral.getManagers().count() == 0, "Peripheral retained its removed entry cable");
        helper.succeed();
    }
}
