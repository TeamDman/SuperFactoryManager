package ca.teamdman.sfm.common.capability;

import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public interface SFMBlockCapabilityProvider<CAP> {
    ///  Used to determine which providers to ask when we are looking for a specific capability kind
    boolean matchesCapabilityKind(SFMBlockCapabilityKind<?> capabilityKind);

    ///  Returns a capability for the given block at the given position in the given level if it has one.
    SFMBlockCapabilityResult<CAP> getCapability(
            SFMBlockCapabilityKind<CAP> capabilityKind,
            World level,
            BlockPos pos,
            IBlockState state,
            @Nullable
            TileEntity blockEntity,
            @Nullable EnumFacing direction
    );

    ///  Higher priority providers are checked first. The Default priority is 0.
    default int priority() {
        return 0;
    }
}
