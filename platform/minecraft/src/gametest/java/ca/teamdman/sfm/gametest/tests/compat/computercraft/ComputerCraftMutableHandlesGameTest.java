package ca.teamdman.sfm.gametest.tests.compat.computercraft;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.compat.computercraft.SFMDiskHandle;
import ca.teamdman.sfm.common.compat.computercraft.SFMInventoryMethods;
import ca.teamdman.sfm.common.compat.computercraft.SFMLabelGunHandle;
import ca.teamdman.sfm.common.compat.computercraft.SFMLabelPositionHolderHandle;
import ca.teamdman.sfm.common.compat.computercraft.SFMManagerHandle;
import ca.teamdman.sfm.common.compat.computercraft.SFMNetworkPeripheral;
import ca.teamdman.sfm.common.compat.computercraft.SFMNetworkPeripheralProvider;
import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfm.common.item.LabelGunItem;
import ca.teamdman.sfm.common.item.LabelGunItem.LabelGunViewMode;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraftforge.items.wrapper.InvWrapper;

/** Covers direct mutable-handle behaviour, including native-size labels and stale sources. */
@SFMGameTest
public class ComputerCraftMutableHandlesGameTest extends SFMGameTestDefinition {
    private static final SFMNetworkPeripheralProvider PROVIDER = new SFMNetworkPeripheralProvider();

    @Override
    public String template() {

        return "6x3x2";
    }

    @Override
    public void run(SFMGameTestHelper helper) {

        BlockPos chestPos = new BlockPos(0, 2, 0);
        BlockPos cablePos = new BlockPos(2, 2, 0);
        BlockPos managerPos = new BlockPos(3, 2, 0);
        helper.setBlock(chestPos, Blocks.CHEST);
        helper.setBlock(cablePos, SFMBlocks.CABLE.get());
        helper.setBlock(managerPos, SFMBlocks.MANAGER.get());

        ChestBlockEntity chest = helper.getBlockEntity(chestPos, ChestBlockEntity.class);
        ItemStack looseDisk = new ItemStack(SFMItems.DISK.get());
        chest.setItem(0, looseDisk);
        SFMDiskHandle looseDiskHandle = SFMInventoryMethods.getSfmDisk(new InvWrapper(chest), 1);
        helper.assertTrue(looseDiskHandle != null, "Inventory methods did not acquire a loose program disk");
        helper.assertTrue(success(looseDiskHandle.setProgram("NAME \"CC mutable handle\"")), "Valid loose disk source failed");
        Object[] invalidProgram = looseDiskHandle.setProgram("EVERY 20 TICKS DO");
        helper.assertTrue(
                Boolean.TRUE.equals(invalidProgram[0]) && "invalid_program".equals(invalidProgram[1]),
                "Invalid loose disk source was not stored with diagnostics"
        );
        helper.assertTrue(!DiskItem.getErrors(looseDisk).isEmpty(), "Invalid loose disk did not retain diagnostics");

        SFMLabelPositionHolderHandle looseLabels = labels(looseDiskHandle);
        for (int label = 0; label < 17; label++) {
            helper.assertTrue(success(looseLabels.add("label_" + label, label, 0, 0)), "Could not add native-size label");
        }
        for (int position = 0; position < 65; position++) {
            helper.assertTrue(success(looseLabels.add("large", position, 1, 0)), "Could not add native-size position");
        }
        helper.assertTrue(
                !success(looseLabels.add("x".repeat(257), 0, 0, 0)),
                "A label name longer than SFM's native limit was accepted"
        );
        helper.assertTrue(success(looseLabels.save()), "Loose disk label session could not save");
        SFMLabelPositionHolderHandle savedLooseLabels = labels(looseDiskHandle);
        helper.assertTrue(savedLooseLabels.labelCount() == 18, "Label session imposed a CC-specific count cap");
        helper.assertTrue(savedLooseLabels.positionCount("large") == 65, "Label session imposed a CC-specific position cap");
        Object[] lastPosition = savedLooseLabels.position("large", 65);
        helper.assertTrue(Integer.valueOf(64).equals(lastPosition[0]), "Label positions were not fully enumerable");

        ItemStack gun = new ItemStack(SFMItems.LABEL_GUN.get());
        chest.setItem(1, gun);
        SFMLabelGunHandle gunHandle = SFMInventoryMethods.getSfmLabelGun(new InvWrapper(chest), 2);
        helper.assertTrue(gunHandle != null, "Inventory methods did not acquire a label gun");
        helper.assertTrue(success(gunHandle.setActiveLabel("ore")), "Could not set active label through handle");
        helper.assertTrue(success(gunHandle.setViewMode("show_only_targeted_block")), "Could not set gun view mode through handle");
        helper.assertTrue(
                "ore".equals(LabelGunItem.getActiveLabel(gun))
                        && LabelGunItem.getViewModeReadOnly(gun) == LabelGunViewMode.SHOW_ONLY_TARGETED_BLOCK,
                "Label gun handle did not mutate normal gun state"
        );
        chest.setItem(1, gun.copy());
        Object[] staleGun = gunHandle.clearActiveLabel();
        helper.assertTrue(
                Boolean.FALSE.equals(staleGun[0]) && "target_changed".equals(staleGun[1]),
                "A stale inventory handle mutated a replacement stack"
        );

        ManagerBlockEntity manager = helper.getBlockEntity(managerPos, ManagerBlockEntity.class);
        ItemStack managerDisk = new ItemStack(SFMItems.DISK.get());
        DiskItem.setProgram(managerDisk, "NAME \"manager labels\"");
        manager.setItem(0, managerDisk);
        manager.rebuildProgramAndUpdateDisk();
        SFMNetworkPeripheral network = (SFMNetworkPeripheral) PROVIDER
                .getPeripheral(helper.getLevel(), helper.absolutePos(cablePos), Direction.NORTH)
                .resolve()
                .orElseThrow();
        SFMManagerHandle managerHandle = network.getManagers().get(1);
        SFMDiskHandle managerDiskHandle = managerHandle.disk();
        SFMLabelPositionHolderHandle firstSession = labels(managerDiskHandle);
        SFMLabelPositionHolderHandle secondSession = labels(managerDiskHandle);
        helper.assertTrue(success(firstSession.add("first", 1, 2, 3)), "First owned label session could not edit");
        helper.assertTrue(success(secondSession.add("second", 4, 5, 6)), "Second owned label session could not edit");
        helper.assertTrue(success(firstSession.save()), "First owned label session could not save");
        helper.assertTrue(success(secondSession.save()), "Second owned label session could not save");
        LabelPositionHolder persisted = LabelPositionHolder.from(managerDisk);
        helper.assertTrue(
                !persisted.contains("first", new BlockPos(1, 2, 3))
                        && persisted.contains("second", new BlockPos(4, 5, 6)),
                "Explicit label-session save was not last-writer-wins"
        );
        helper.assertTrue(
                manager.getProgram() != null && manager.getStateReadOnly() == ManagerBlockEntity.State.RUNNING,
                "Manager disk label save did not rebuild manager state"
        );
        helper.succeed();
    }

    private static SFMLabelPositionHolderHandle labels(SFMDiskHandle disk) {

        return disk.labels();
    }

    private static boolean success(Object[] result) {

        return Boolean.TRUE.equals(result[0]);
    }
}
