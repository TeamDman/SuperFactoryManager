package ca.teamdman.sfm.common.compat.computercraft;

import ca.teamdman.sfm.common.block_network.CableNetwork;
import ca.teamdman.sfm.common.block_network.CableNetworkManager;
{% case minecraft_version %}
{% when "1.20.4", "1.21", "1.21.1", "26.1.2" %}
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
{% endcase %}
import dan200.computercraft.api.peripheral.IPeripheral;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3" %}
import dan200.computercraft.api.peripheral.IPeripheralProvider;
{% endcase %}
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3" %}
import net.minecraftforge.common.util.LazyOptional;

import javax.annotation.Nonnull;
{% when "1.20.4", "1.21", "1.21.1", "26.1.2" %}
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.IBlockCapabilityProvider;
import org.jetbrains.annotations.Nullable;
{% endcase %}

/**
 * Exposes the cable network at every SFM cable-member position.
 */
{% case minecraft_version %}
{% when "1.20.4" %}
@MCVersionDependentBehaviour // CC:Tweaked 1.110.2+ uses NeoForge block capabilities
{% when "1.21" %}
@MCVersionDependentBehaviour // CC:Tweaked 1.111.0+ uses NeoForge block capabilities
{% when "1.21.1", "26.1.2" %}
@MCVersionDependentBehaviour // CC:Tweaked 1.113.1+ uses NeoForge block capabilities
{% endcase %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3" %}
public final class SFMNetworkPeripheralProvider implements IPeripheralProvider {
{% when "1.20.4", "1.21", "1.21.1", "26.1.2" %}
public final class SFMNetworkPeripheralProvider implements IBlockCapabilityProvider<IPeripheral, Direction> {
{% endcase %}
    @Override
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3" %}
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
{% when "1.20.4", "1.21", "1.21.1", "26.1.2" %}
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

{% endcase %}
}
