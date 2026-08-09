package ca.teamdman.sfm.common.compat.computercraft;

import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** Lua entry point for the SFM cable network reachable from one cable position. */
public final class SFMNetworkPeripheral implements IPeripheral {
    public static final String TYPE = "sfm_network";

    private final Level level;
    private final BlockPos cablePos;

    public SFMNetworkPeripheral(
            Level level,
            BlockPos cablePos
    ) {

        this.level = level;
        this.cablePos = cablePos.immutable();
    }

    @Override
    public String getType() {

        return TYPE;
    }

    @Override
    public boolean equals(@Nullable IPeripheral other) {

        return other instanceof SFMNetworkPeripheral otherNetwork
               && level == otherNetwork.level
               && cablePos.equals(otherNetwork.cablePos);
    }

    /**
     * Acquires a collection whose entries are manager handles, rather than materialising a Lua
     * table snapshot of every manager and disk in the network.
     */
    @LuaFunction
    public final SFMManagerCollectionHandle getManagers() {

        return new SFMManagerCollectionHandle(level, cablePos);
    }
}
