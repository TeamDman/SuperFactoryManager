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
import net.minecraftforge.energy.IEnergyStorage;
{% when '1.20.2', '1.20.3', '1.20.4', '1.21' %}
import net.neoforged.neoforge.energy.IEnergyStorage;
{% when '1.21.1', '26.1.2' %}
import net.neoforged.neoforge.common.extensions.ILevelExtension;
import net.neoforged.neoforge.energy.IEnergyStorage;
{% endcase %}
import org.jetbrains.annotations.Nullable;

public class EnergyAcceptorBlockCapabilityProvider implements SFMBlockCapabilityProvider<IEnergyStorage> {
    @Override
    public boolean matchesCapabilityKind(SFMBlockCapabilityKind<?> capabilityKind) {
        return SFMWellKnownCapabilities.ENERGY.equals(capabilityKind);
    }

    @MCVersionDependentBehaviour
    @Override
    public SFMBlockCapabilityResult<IEnergyStorage> getCapability(
            SFMBlockCapabilityKind<IEnergyStorage> capabilityKind,
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
        IEnergyStorage energyStorage = capLevel.getCapability(
                capabilityKind.capabilityKind(),
                pos,
                state,
                blockEntity,
                direction
        );
        if (energyStorage == null) return SFMBlockCapabilityResult.empty();
        return SFMBlockCapabilityResult.of(new EnergyAcceptorEnergyStorageWrapper(energyStorage));
{% endcase %}
    }

}
