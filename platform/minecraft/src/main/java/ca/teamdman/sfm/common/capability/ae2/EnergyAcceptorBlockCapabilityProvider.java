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
import net.neoforged.neoforge.common.extensions.ILevelExtension;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
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
            LevelAccessor level,
            BlockPos pos,
            BlockState state,
            @Nullable BlockEntity blockEntity,
            @Nullable Direction direction
    ) {
        if (!(level instanceof ILevelExtension capLevel)) return SFMBlockCapabilityResult.empty();
        if (!(blockEntity instanceof EnergyAcceptorBlockEntity)) return SFMBlockCapabilityResult.empty();

        EnergyHandler energyStorage = capLevel.getCapability(
                capabilityKind.capabilityKind(),
                pos,
                state,
                blockEntity,
                direction
        );
        if (energyStorage == null) return SFMBlockCapabilityResult.empty();
        return SFMBlockCapabilityResult.of(new EnergyAcceptorEnergyStorageWrapper(energyStorage));
    }

}
