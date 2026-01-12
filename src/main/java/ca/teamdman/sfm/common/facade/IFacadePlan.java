package ca.teamdman.sfm.common.facade;

import ca.teamdman.sfm.common.util.ConfirmationParams;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public interface IFacadePlan {
    void apply(World level);
    Set<BlockPos> positions();
    @Nullable ConfirmationParams computeWarning(World level);
}
