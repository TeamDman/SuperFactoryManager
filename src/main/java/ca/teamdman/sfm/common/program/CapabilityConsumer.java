package ca.teamdman.sfm.common.program;

import ca.teamdman.sfml.ast.Label;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.EnumFacing;

@FunctionalInterface
public interface CapabilityConsumer<T> {
    void accept(
            Label label,
            BlockPos pos,
            EnumFacing direction,
            T cap
    );
}
