package ca.teamdman.sfm.common.program;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import ca.teamdman.sfml.ast.Label;

@FunctionalInterface
public interface CapabilityConsumer<T> {

    void accept(
                Label label,
                BlockPos pos,
                EnumFacing direction,
                T cap);
}
