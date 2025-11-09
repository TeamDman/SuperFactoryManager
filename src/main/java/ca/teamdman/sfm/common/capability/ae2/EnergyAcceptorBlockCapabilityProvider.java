package ca.teamdman.sfm.common.capability.ae2;

import appeng.tile.networking.TileEnergyAcceptor;
import ca.teamdman.sfm.common.capability.SFMBlockCapabilityKind;
import ca.teamdman.sfm.common.capability.SFMBlockCapabilityProvider;
import ca.teamdman.sfm.common.capability.SFMBlockCapabilityResult;
import ca.teamdman.sfm.common.capability.SFMWellKnownCapabilities;
import ca.teamdman.sfm.common.capability.energystorage.EnergyAcceptorEnergyStorageWrapper;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.energy.IEnergyStorage;
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
            World level,
            BlockPos pos,
            IBlockState state,
            @Nullable TileEntity blockEntity,
            @Nullable EnumFacing direction
    ) {
        if (blockEntity instanceof TileEnergyAcceptor energyAcceptor) {
            var capability = energyAcceptor.getCapability(SFMWellKnownCapabilities.ENERGY.capabilityKind(), null);
            return SFMBlockCapabilityResult.of(
                    capability != null ? new EnergyAcceptorEnergyStorageWrapper(capability) : null

            );
        } else {
            return SFMBlockCapabilityResult.empty();
        }
    }

}
