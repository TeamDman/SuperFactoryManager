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
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
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
            LevelAccessor level,
            BlockPos pos,
            BlockState state,
            @Nullable BlockEntity blockEntity,
            @Nullable Direction direction
    ) {
        if (blockEntity instanceof EnergyAcceptorBlockEntity energyAcceptor) {
            return SFMBlockCapabilityResult.of(
                    energyAcceptor.getCapability(SFMWellKnownCapabilities.ENERGY.capabilityKind())
                            .lazyMap(EnergyAcceptorEnergyStorageWrapper::new)

            );
        } else {
            return SFMBlockCapabilityResult.empty();
        }
    }

}
