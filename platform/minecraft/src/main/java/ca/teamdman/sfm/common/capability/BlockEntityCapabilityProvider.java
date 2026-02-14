package ca.teamdman.sfm.common.capability;

import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/// In NeoForge for Minecraft 1.20.3, the way capabilities are discovered changed.
/// See {@link SFMBlockCapabilityProvider} for more information.
/// This is the fallback provider for the "built-in" behaviour provided by the modding framework.
public class BlockEntityCapabilityProvider implements SFMBlockCapabilityProvider<Object> {
    @Override
    public boolean matchesCapabilityKind(SFMBlockCapabilityKind<?> capabilityKind) {
        return true;
    }

    @Override
    public SFMBlockCapabilityResult<Object> getCapability(
            SFMBlockCapabilityKind<Object> capabilityKind,
            World level,
            BlockPos pos,
            IBlockState state,
            @Nullable TileEntity blockEntity,
            @Nullable EnumFacing direction
    ) {
        if (blockEntity != null) {
            Object result = blockEntity.getCapability(capabilityKind.capabilityKind().get(), direction);
            if (result != null) {
                return SFMBlockCapabilityResult.of(result);
            }
        }
        return SFMBlockCapabilityResult.empty();
    }

    @Override
    public int priority() {
        return -100; // check this one last
    }
}
