package ca.teamdman.sfm.gametest.tests.compat.computercraft;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.compat.computercraft.SFMNetworkPeripheral;
import ca.teamdman.sfm.common.compat.computercraft.SFMNetworkPeripheralProvider;
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

import java.util.List;
import java.util.Map;

@SFMGameTest
public class ComputerCraftNetworkPeripheralGameTest extends SFMGameTestDefinition {
    private static final SFMNetworkPeripheralProvider PROVIDER = new SFMNetworkPeripheralProvider();

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
        ItemStack disk = new ItemStack(SFMItems.DISK.get());
        firstManager.setItem(0, disk);
        firstManager.setProgram("NAME \"CC network test\"");
        LabelPositionHolder
                .from(disk)
                .add("source", helper.absolutePos(new BlockPos(0, 2, 0)))
                .save(disk);

        var peripheral = PROVIDER
                .getPeripheral(helper.getLevel(), helper.absolutePos(cablePos), Direction.NORTH)
                .resolve()
                .orElseThrow();
        helper.assertTrue(
                peripheral instanceof SFMNetworkPeripheral,
                "SFM cable did not expose an SFM network peripheral"
        );
        helper.assertTrue(
                SFMNetworkPeripheral.TYPE.equals(peripheral.getType()),
                "SFM cable peripheral reported an unexpected type: " + peripheral.getType()
        );

        List<Map<String, Object>> managers = ((SFMNetworkPeripheral) peripheral).getManagers();
        helper.assertTrue(managers.size() == 2, "SFM network did not enumerate both connected managers");

        var directManagerPeripheral = PROVIDER
                .getPeripheral(helper.getLevel(), helper.absolutePos(firstManagerPos), Direction.NORTH)
                .resolve()
                .orElseThrow();
        helper.assertTrue(
                ((SFMNetworkPeripheral) directManagerPeripheral).getManagers().size() == 2,
                "An SFM manager did not expose the network reached through its own cable membership"
        );

        var managerlessPeripheral = PROVIDER
                .getPeripheral(helper.getLevel(), helper.absolutePos(managerlessCablePos), Direction.NORTH)
                .resolve()
                .orElseThrow();
        helper.assertTrue(
                ((SFMNetworkPeripheral) managerlessPeripheral).getManagers().isEmpty(),
                "A managerless SFM cable did not expose the documented empty network view"
        );

        Map<String, Object> firstManagerDetails = managers.get(0);
        Map<?, ?> position = (Map<?, ?>) firstManagerDetails.get("position");
        helper.assertTrue(
                Integer.valueOf(helper.absolutePos(firstManagerPos).getX()).equals(position.get("x")),
                "Manager enumeration was not deterministic"
        );
        helper.assertTrue(
                "running".equals(firstManagerDetails.get("state")),
                "Manager state was not exposed as read-only network data"
        );

        Map<?, ?> diskDetails = (Map<?, ?>) firstManagerDetails.get("disk");
        helper.assertTrue(diskDetails != null, "Manager disk was not exposed through the network");
        helper.assertTrue(
                "CC network test".equals(diskDetails.get("name")),
                "Disk name was not exposed through the network"
        );
        helper.assertTrue(
                "NAME \"CC network test\"".equals(diskDetails.get("program")),
                "Disk program was not exposed through the network"
        );
        Map<?, ?> labels = (Map<?, ?>) diskDetails.get("labels");
        helper.assertTrue(labels.containsKey("source"), "Disk labels were not exposed through the network");

        helper.setBlock(bridgeCablePos, net.minecraft.world.level.block.Blocks.AIR);
        helper.assertTrue(
                ((SFMNetworkPeripheral) peripheral).getManagers().size() == 1,
                "Peripheral did not re-resolve to its split cable network"
        );

        helper.setBlock(bridgeCablePos, SFMBlocks.CABLE.get());
        helper.assertTrue(
                ((SFMNetworkPeripheral) peripheral).getManagers().size() == 2,
                "Peripheral did not re-resolve after its cable networks rejoined"
        );

        helper.setBlock(cablePos, net.minecraft.world.level.block.Blocks.AIR);
        helper.assertTrue(
                ((SFMNetworkPeripheral) peripheral).getManagers().isEmpty(),
                "Peripheral retained a stale cable network after its entry cable was removed"
        );
        helper.succeed();
    }
}
