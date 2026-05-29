package ca.teamdman.sfm.common.capability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.IBlockCapabilityProvider;
import org.jetbrains.annotations.Nullable;

/// In NeoForge for Minecraft 1.20.3, the way capabilities are discovered changed.
/// See {@link SFMBlockCapabilityProvider} for more information.
/// This is the fallback provider for the "built-in" behaviour provided by the modding framework.
public class RedstoneSignalCapabilityProvider implements SFMBlockCapabilityProvider<IRedstoneSignalStorage>, IBlockCapabilityProvider<IRedstoneSignalStorage, @Nullable Direction> {

    @Override
    public boolean matchesCapabilityKind(SFMBlockCapabilityKind<?> capabilityKind) {
        return capabilityKind.equals(SFMWellKnownCapabilities.REDSTONE_HANDLER);
    }

    @Override
    public SFMBlockCapabilityResult<IRedstoneSignalStorage> getCapability(
            SFMBlockCapabilityKind<IRedstoneSignalStorage> capabilityKind,
            LevelAccessor level,
            BlockPos pos,
            BlockState state,
            @Nullable BlockEntity blockEntity,
            @Nullable Direction direction
    ) {
        try {
            // Wrap in try-catch since getSignal doesn't explicitly allow the null direction
            @SuppressWarnings("DataFlowIssue")
            int signal = state.getSignal(level, pos, direction);
            return SFMBlockCapabilityResult.of(new RedstoneSignalStorage(signal, 15));
        } catch (Throwable t) {
            return SFMBlockCapabilityResult.empty();
        }
    }

    @Override
    public @Nullable IRedstoneSignalStorage getCapability(
            Level level,
            BlockPos pos,
            BlockState state,
            @Nullable BlockEntity blockEntity,
            @Nullable Direction context
    ) {

        try {
            // Wrap in try-catch since getSignal doesn't explicitly allow the null direction
            @SuppressWarnings("DataFlowIssue")
            int signal = state.getSignal(level, pos, context);
            return new RedstoneSignalStorage(signal, 15);
        } catch (Throwable t) {
            return null;
        }
    }

}
