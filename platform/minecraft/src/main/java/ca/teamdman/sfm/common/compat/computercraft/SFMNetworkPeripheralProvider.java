package ca.teamdman.sfm.common.compat.computercraft;

import ca.teamdman.sfm.common.block_network.CableNetwork;
import ca.teamdman.sfm.common.block_network.CableNetworkManager;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.IPeripheralProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.util.LazyOptional;

import javax.annotation.Nonnull;

/**
 * Exposes the cable network at every SFM cable-member position.
 */
public final class SFMNetworkPeripheralProvider implements IPeripheralProvider {
    @Override
    public @Nonnull LazyOptional<IPeripheral> getPeripheral(
            @Nonnull Level level,
            @Nonnull BlockPos pos,
            @Nonnull Direction side
    ) {

        if (level.isClientSide() || !CableNetwork.isCable(level, pos)) {
            return LazyOptional.empty();
        }
        if (CableNetworkManager.getOrRegisterNetworkFromCablePosition(level, pos).isEmpty()) {
            return LazyOptional.empty();
        }
        return LazyOptional.of(() -> new SFMNetworkPeripheral(level, pos.immutable()));
    }
}
