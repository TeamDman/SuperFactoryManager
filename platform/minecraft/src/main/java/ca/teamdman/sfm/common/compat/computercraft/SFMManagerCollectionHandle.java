package ca.teamdman.sfm.common.compat.computercraft;

import ca.teamdman.sfm.common.block_network.CableNetwork;
import ca.teamdman.sfm.common.block_network.CableNetworkManager;
import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import dan200.computercraft.api.lua.LuaFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.List;

/** A live, one-indexed collection of the managers currently on an SFM cable network. */
public final class SFMManagerCollectionHandle {
    private final Level level;
    private final BlockPos cablePos;

    SFMManagerCollectionHandle(
            Level level,
            BlockPos cablePos
    ) {

        this.level = level;
        this.cablePos = cablePos.immutable();
    }

    @LuaFunction(mainThread = true)
    public final int count() {

        return managers().size();
    }

    @LuaFunction
    public final SFMManagerHandle get(int index) {

        return index < 1 ? null : new SFMManagerHandle(level, cablePos, index);
    }

    private List<ManagerBlockEntity> managers() {

        return CableNetworkManager
                .getNetworkFromCablePosition(level, cablePos)
                .stream()
                .flatMap(CableNetwork::getManagers)
                .toList();
    }
}
