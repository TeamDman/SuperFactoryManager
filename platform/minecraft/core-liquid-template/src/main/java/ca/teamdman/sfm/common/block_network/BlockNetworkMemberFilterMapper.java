package ca.teamdman.sfm.common.block_network;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/// A function that determines if a block position is a valid member of a network.
/// Returns null if the position is not a valid member, otherwise returns the member object.
@FunctionalInterface
public interface BlockNetworkMemberFilterMapper<LEVEL, T> {
    @Nullable T getNetworkMember(LEVEL level, BlockPos pos);
}
