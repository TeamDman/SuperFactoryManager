package ca.teamdman.sfm.common.compat.computercraft;

import ca.teamdman.sfm.common.block_network.CableNetwork;
import ca.teamdman.sfm.common.block_network.CableNetworkManager;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.IBlockCapabilityProvider;
import org.jetbrains.annotations.Nullable;

/**
 * Exposes the cable network at every SFM cable-member position.
 */
@MCVersionDependentBehaviour // CC:Tweaked 1.111.0+ uses NeoForge block capabilities
public final class SFMNetworkPeripheralProvider implements IBlockCapabilityProvider<IPeripheral, Direction> {
    @Override
    public @Nullable IPeripheral getCapability(
            Level level,
            BlockPos pos,
            BlockState state,
            @Nullable BlockEntity blockEntity,
            @Nullable Direction side
    ) {

        if (level.isClientSide() || !CableNetwork.isCable(level, pos)) return null;
        if (CableNetworkManager.getOrRegisterNetworkFromCablePosition(level, pos).isEmpty()) return null;
        return new SFMNetworkPeripheral(level, pos.immutable());
    }
}
