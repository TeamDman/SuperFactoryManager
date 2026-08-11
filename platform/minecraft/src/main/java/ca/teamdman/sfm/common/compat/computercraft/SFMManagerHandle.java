package ca.teamdman.sfm.common.compat.computercraft;

import ca.teamdman.sfm.common.block_network.CableNetwork;
import ca.teamdman.sfm.common.block_network.CableNetworkManager;
import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfml.ast.Program;
import dan200.computercraft.api.lua.LuaFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * A manager selected from a specific cable entry point.
 *
 * <p>It never trusts the original network topology: every operation verifies that the original
 * manager instance remains reachable from that entry cable.</p>
 */
public final class SFMManagerHandle {
    private final Level level;
    private final BlockPos cablePos;
    private final int managerIndex;
    private @Nullable ManagerBlockEntity expectedManager;

    SFMManagerHandle(
            Level level,
            BlockPos cablePos,
            int managerIndex
    ) {

        this.level = level;
        this.cablePos = cablePos.immutable();
        this.managerIndex = managerIndex;
    }

    @LuaFunction(mainThread = true)
    public final Object[] position() {

        ManagerBlockEntity manager = resolveManager();
        if (manager == null) {
            return SFMComputerCraftResults.unavailable("manager_unreachable");
        }
        BlockPos position = manager.getBlockPos();
        return new Object[]{position.getX(), position.getY(), position.getZ()};
    }

    @LuaFunction(mainThread = true)
    public final Object[] state() {

        ManagerBlockEntity manager = resolveManager();
        return manager == null
               ? SFMComputerCraftResults.unavailable("manager_unreachable")
               : new Object[]{manager.getStateReadOnly().name().toLowerCase(Locale.ROOT)};
    }

    @LuaFunction
    public final SFMDiskHandle disk() {

        return new SFMDiskHandle(diskTarget());
    }

    private SFMItemHandleTarget diskTarget() {

        ItemStack[] expectedDisk = {null};
        return new SFMItemHandleTarget(
                () -> {
                    ManagerBlockEntity manager = resolveManager();
                    if (manager == null) {
                        return SFMItemHandleTarget.Resolution.failure("manager_unreachable");
                    }
                    ItemStack currentDisk = manager.getDisk();
                    if (currentDisk == null) {
                        return SFMItemHandleTarget.Resolution.failure("no_disk");
                    }
                    if (expectedDisk[0] == null) {
                        expectedDisk[0] = currentDisk;
                        return SFMItemHandleTarget.Resolution.success(currentDisk);
                    }
                    return currentDisk == expectedDisk[0]
                           ? SFMItemHandleTarget.Resolution.success(currentDisk)
                           : SFMItemHandleTarget.Resolution.failure("target_changed");
                },
                ignored -> {
                },
                this::rebuildManagerDisk
        );
    }

    @Nullable ManagerBlockEntity resolveManager() {

        List<ManagerBlockEntity> managers = CableNetworkManager
                .getNetworkFromCablePosition(level, cablePos)
                .stream()
                .flatMap(CableNetwork::getManagers)
                .toList();
        if (expectedManager == null) {
            if (managerIndex < 1 || managerIndex > managers.size()) {
                return null;
            }
            expectedManager = managers.get(managerIndex - 1);
        }
        return managers.contains(expectedManager) ? expectedManager : null;
    }

    @Nullable Program rebuildManagerDisk(ItemStack ignored) {

        ManagerBlockEntity manager = resolveManager();
        if (manager == null) {
            return null;
        }
        manager.ensureRebuildWarnings();
        manager.rebuildProgramAndUpdateDisk();
        manager.setChanged();
        return manager.getProgram();
    }
}
