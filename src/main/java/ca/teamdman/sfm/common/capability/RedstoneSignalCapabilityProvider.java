package ca.teamdman.sfm.common.capability;

import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/// In NeoForge for Minecraft 1.20.3, the way capabilities are discovered changed.
/// See {@link SFMBlockCapabilityProvider} for more information.
/// This is the fallback provider for the "built-in" behaviour provided by the modding framework.
public class RedstoneSignalCapabilityProvider implements SFMBlockCapabilityProvider<IRedstoneSignalStorage> {
    @Override
    public boolean matchesCapabilityKind(SFMBlockCapabilityKind<?> capabilityKind) {
        return capabilityKind.equals(SFMWellKnownCapabilities.REDSTONE_HANDLER);
    }

    @Override
    public SFMBlockCapabilityResult<IRedstoneSignalStorage> getCapability(
            SFMBlockCapabilityKind<IRedstoneSignalStorage> capabilityKind,
            World levelAccessor,
            BlockPos pos,
            IBlockState state,
            @Nullable TileEntity blockEntity,
            @Nullable EnumFacing direction
    ) {
        return SFMBlockCapabilityResult.of(new RedstoneSignalBlockReader(levelAccessor, pos, direction));
    }
}
