package ca.teamdman.sfm.common.capability;

{% if features.redstone_live_read %}
import ca.teamdman.sfm.common.block.BufferBlock;
import ca.teamdman.sfm.common.util.SFMDirections;
{% endif %}
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
{% if features.redstone_live_read %}
import net.minecraft.util.Mth;
{% endif %}
{% case minecraft_version %}
{% when "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
import net.minecraft.world.level.Level;
{% endcase %}
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
{% case minecraft_version %}
{% when "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
import net.neoforged.neoforge.capabilities.IBlockCapabilityProvider;
{% endcase %}
import org.jetbrains.annotations.Nullable;

{% if features.redstone_live_read %}
/// Exposes a block's emitted signal as a live, read-only resource query.
/// The network may cache the handler, but must never cache the measured strength.
{% else %}
/// In NeoForge for Minecraft 1.20.3, the way capabilities are discovered changed.
/// See {@link SFMBlockCapabilityProvider} for more information.
/// This is the fallback provider for the "built-in" behaviour provided by the modding framework.
{% endif %}
{% if features.redstone_live_read %}
public class RedstoneSignalCapabilityProvider implements SFMBlockCapabilityProvider<IRedstoneSignalStorage> {
{% else %}
{% case minecraft_version %}
{% when "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
public class RedstoneSignalCapabilityProvider implements SFMBlockCapabilityProvider<IRedstoneSignalStorage>, IBlockCapabilityProvider<IRedstoneSignalStorage, @Nullable Direction> {

{% else %}
public class RedstoneSignalCapabilityProvider implements SFMBlockCapabilityProvider<RedstoneSignalStorage> {
{% endcase %}
{% endif %}
{% if features.redstone_live_read %}
    @Override
    public int priority() {
        // Actual stored redstone (such as a buffer capability) takes precedence
        // over measuring the block's emitted world signal.
        return -200;
    }

{% endif %}
    @Override
    public boolean matchesCapabilityKind(SFMBlockCapabilityKind<?> capabilityKind) {
        return capabilityKind.equals(SFMWellKnownCapabilities.REDSTONE_HANDLER);
    }

    @Override
{% if features.redstone_live_read %}
    public SFMBlockCapabilityResult<IRedstoneSignalStorage> getCapability(
            SFMBlockCapabilityKind<IRedstoneSignalStorage> capabilityKind,
{% else %}
{% case minecraft_version %}
{% when "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
    public SFMBlockCapabilityResult<IRedstoneSignalStorage> getCapability(
            SFMBlockCapabilityKind<IRedstoneSignalStorage> capabilityKind,
{% else %}
    public SFMBlockCapabilityResult<RedstoneSignalStorage> getCapability(
            SFMBlockCapabilityKind<RedstoneSignalStorage> capabilityKind,
{% endcase %}
{% endif %}
{% case minecraft_version %}
{% when "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
            LevelAccessor level,
{% else %}
            LevelAccessor levelAccessor,
{% endcase %}
            BlockPos pos,
            BlockState state,
            @Nullable BlockEntity blockEntity,
            @Nullable Direction direction
    ) {
{% if features.redstone_live_read %}
        // An occupied buffer can temporarily hide its redstone capability.
        // Do not cache a world-signal fallback that would hide later storage.
        if (state.getBlock() instanceof BufferBlock) return SFMBlockCapabilityResult.empty();
        return SFMBlockCapabilityResult.of(new WorldSignal(levelAccessor, pos.immutable(), direction));
{% else %}
        try {
            // Wrap in try-catch since getSignal doesn't explicitly allow the null direction
            @SuppressWarnings("DataFlowIssue")
{% case minecraft_version %}
{% when "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
            int signal = state.getSignal(level, pos, direction);
{% else %}
            int signal = state.getSignal(levelAccessor, pos, direction);
{% endcase %}
            return SFMBlockCapabilityResult.of(new RedstoneSignalStorage(signal, 15));
        } catch (Throwable t) {
            return SFMBlockCapabilityResult.empty();
        }
{% endif %}
    }
{% case minecraft_version %}
{% when "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}

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

{% endcase %}
{% if features.redstone_live_read %}

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
{% endif %}
}
