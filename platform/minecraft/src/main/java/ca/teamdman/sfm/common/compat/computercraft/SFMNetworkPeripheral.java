package ca.teamdman.sfm.common.compat.computercraft;

import ca.teamdman.sfm.common.block_network.CableNetwork;
import ca.teamdman.sfm.common.block_network.CableNetworkManager;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Read-only Lua view of the SFM cable network reachable from one cable position.
 */
public final class SFMNetworkPeripheral implements IPeripheral {
    public static final String TYPE = "sfm_network";
    public static final int MAX_MANAGER_RESULTS = 16;

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
     * Returns up to {@value MAX_MANAGER_RESULTS} loaded managers in this cable network as deterministic, read-only Lua tables.
     */
    @LuaFunction(mainThread = true)
    public final List<Map<String, Object>> getManagers() {

        return getLoadedManagers()
                .limit(MAX_MANAGER_RESULTS)
                .map(SFMComputerCraftData::managerDetails)
                .toList();
    }

    /**
     * Returns the total number of loaded managers currently in the cable network.
     */
    @LuaFunction(mainThread = true)
    public final long getManagerCount() {

        return getLoadedManagers().count();
    }

    private java.util.stream.Stream<ca.teamdman.sfm.common.blockentity.ManagerBlockEntity> getLoadedManagers() {

        return getNetwork()
                .stream()
                .flatMap(CableNetwork::getManagers);
    }

    private java.util.Optional<CableNetwork> getNetwork() {

        return CableNetworkManager.getNetworkFromCablePosition(level, cablePos);
    }

}
