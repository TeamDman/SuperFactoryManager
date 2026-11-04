package ca.teamdman.sfm.common.capability.ae2;

import appeng.blockentity.networking.EnergyAcceptorBlockEntity;
import ca.teamdman.sfm.common.capability.SFMBlockCapabilityKind;
import ca.teamdman.sfm.common.capability.SFMBlockCapabilityProvider;
import ca.teamdman.sfm.common.capability.SFMBlockCapabilityResult;
import ca.teamdman.sfm.common.capability.SFMWellKnownCapabilities;
import ca.teamdman.sfm.common.capability.energystorage.EnergyAcceptorEnergyStorageWrapper;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.21.1', '26.1.2' %}
import net.minecraft.world.level.LevelAccessor;
{% when '1.20.3', '1.20.4', '1.21' %}
import net.minecraft.world.level.Level;
{% endcase %}
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
import net.minecraftforge.energy.EnergyHandler;
{% when '1.20.2', '1.20.3', '1.20.4', '1.21' %}
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
{% when '1.21.1', '26.1.2' %}
import net.neoforged.neoforge.common.extensions.ILevelExtension;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
{% endcase %}
import org.jetbrains.annotations.Nullable;

public class EnergyAcceptorBlockCapabilityProvider implements SFMBlockCapabilityProvider<EnergyHandler> {
    @Override
    public boolean matchesCapabilityKind(SFMBlockCapabilityKind<?> capabilityKind) {
        return SFMWellKnownCapabilities.ENERGY.equals(capabilityKind);
    }

    @MCVersionDependentBehaviour
    @Override
    public SFMBlockCapabilityResult<EnergyHandler> getCapability(
            SFMBlockCapabilityKind<EnergyHandler> capabilityKind,
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.21.1', '26.1.2' %}
            LevelAccessor level,
{% when '1.20.3', '1.20.4', '1.21' %}
            Level level,
{% endcase %}
            BlockPos pos,
            BlockState state,
            @Nullable BlockEntity blockEntity,
            @Nullable Direction direction
    ) {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2' %}
        if (blockEntity instanceof EnergyAcceptorBlockEntity energyAcceptor) {
            return SFMBlockCapabilityResult.of(
                    energyAcceptor.getCapability(SFMWellKnownCapabilities.ENERGY.capabilityKind())
                            .lazyMap(EnergyAcceptorEnergyStorageWrapper::new)
{% when '1.20.3', '1.20.4', '1.21' %}
        if (blockEntity instanceof EnergyAcceptorBlockEntity energyAcceptor) {
            return SFMBlockCapabilityResult.create(
                    energyAcceptor.getCapability(SFMWellKnownCapabilities.ENERGY.capabilityKind())
                            .lazyMap(EnergyAcceptorEnergyStorageWrapper::new)
{% when '1.21.1', '26.1.2' %}
        if (!(level instanceof ILevelExtension capLevel)) return SFMBlockCapabilityResult.empty();
        if (!(blockEntity instanceof EnergyAcceptorBlockEntity)) return SFMBlockCapabilityResult.empty();
{% endcase %}

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21' %}
            );
        } else {
            return SFMBlockCapabilityResult.empty();
        }
{% when '1.21.1', '26.1.2' %}
        EnergyHandler energyStorage = capLevel.getCapability(
                capabilityKind.capabilityKind(),
                pos,
                state,
                blockEntity,
                direction
        );
        if (energyStorage == null) return SFMBlockCapabilityResult.empty();
        return SFMBlockCapabilityResult.of(new EnergyHandler() {
            @Override
            public long getAmountAsLong() {
                return energyStorage.getAmountAsLong();
            }

            @Override
            public long getCapacityAsLong() {
                // AE2's acceptor reports zero capacity; preserve the legacy wrapper's insertion hint.
                return Integer.MAX_VALUE;
            }

            @Override
            public int insert(int amount, TransactionContext transaction) {
                return energyStorage.insert(amount, transaction);
            }

            @Override
            public int extract(int amount, TransactionContext transaction) {
                return energyStorage.extract(amount, transaction);
            }
        });
{% endcase %}
    }

}
