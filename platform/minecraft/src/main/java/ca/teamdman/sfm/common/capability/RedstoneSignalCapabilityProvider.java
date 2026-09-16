package ca.teamdman.sfm.common.capability;

import ca.teamdman.sfm.common.block.BufferBlock;
import ca.teamdman.sfm.common.util.SFMDirections;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/// Exposes a block's emitted signal as a live, read-only resource query.
/// The network may cache the handler, but must never cache the measured strength.
public class RedstoneSignalCapabilityProvider implements SFMBlockCapabilityProvider<IRedstoneSignalStorage> {
    @Override
    public int priority() {
        // Actual stored redstone (such as a buffer capability) takes precedence
        // over measuring the block's emitted world signal.
        return -200;
    }

    @Override
    public boolean matchesCapabilityKind(SFMBlockCapabilityKind<?> capabilityKind) {
        return capabilityKind.equals(SFMWellKnownCapabilities.REDSTONE_HANDLER);
    }

    @Override
    public SFMBlockCapabilityResult<IRedstoneSignalStorage> getCapability(
            SFMBlockCapabilityKind<IRedstoneSignalStorage> capabilityKind,
            LevelAccessor levelAccessor,
            BlockPos pos,
            BlockState state,
            @Nullable BlockEntity blockEntity,
            @Nullable Direction direction
    ) {
        // An occupied buffer can temporarily hide its redstone capability.
        // Do not cache a world-signal fallback that would hide later storage.
        if (state.getBlock() instanceof BufferBlock) return SFMBlockCapabilityResult.empty();
        return SFMBlockCapabilityResult.of(new WorldSignal(levelAccessor, pos.immutable(), direction));
    }

    private record WorldSignal(
            LevelAccessor level,
            BlockPos pos,
            @Nullable Direction side
    ) implements IRedstoneSignalStorage {
        @Override
        public int getStoredAmount() {
            if (!level.hasChunkAt(pos)) return 0;
            BlockState current = level.getBlockState(pos);
            if (side != null) {
                // Minecraft's query direction points from the receiver toward the
                // source; SFML sides name the source's outward-facing block face.
                return Mth.clamp(current.getSignal(level, pos, side.getOpposite()), 0, 15);
            }
            int strongest = 0;
            for (Direction direction : SFMDirections.DIRECTIONS_WITHOUT_NULL) {
                strongest = Math.max(strongest, current.getSignal(level, pos, direction));
                if (strongest >= 15) return 15;
            }
            return strongest;
        }

        @Override
        public int getMaxStoredAmount() {
            return 15;
        }

        @Override
        public int insert(int amount, boolean simulate) {
            return 0;
        }

        @Override
        public int extract(int amount, boolean simulate) {
            return 0;
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return false;
        }
    }
}
