package ca.teamdman.sfm.common.capability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/// In NeoForge for Minecraft 1.20.3, the way capabilities are discovered changed.
/// See {@link SFMBlockCapabilityProvider} for more information.
/// This is the fallback provider for the "built-in" behaviour provided by the modding framework.
public class RedstoneSignalCapabilityProvider implements SFMBlockCapabilityProvider<RedstoneSignalStorage> {
    @Override
    public boolean matchesCapabilityKind(SFMBlockCapabilityKind<?> capabilityKind) {
        return capabilityKind.equals(SFMWellKnownCapabilities.REDSTONE_HANDLER);
    }

    @Override
    public SFMBlockCapabilityResult<RedstoneSignalStorage> getCapability(
            SFMBlockCapabilityKind<RedstoneSignalStorage> capabilityKind,
            LevelAccessor levelAccessor,
            BlockPos pos,
            BlockState state,
            @Nullable BlockEntity blockEntity,
            @Nullable Direction direction
    ) {
        try {
            // Wrap in try-catch since getSignal doesn't explicitly allow the null direction
            @SuppressWarnings("DataFlowIssue")
            int signal = state.getSignal(levelAccessor, pos, direction);
            return SFMBlockCapabilityResult.of(new RedstoneSignalStorage(signal, 15));
        } catch (Exception e) {
            return SFMBlockCapabilityResult.empty();
        }
    }
}
