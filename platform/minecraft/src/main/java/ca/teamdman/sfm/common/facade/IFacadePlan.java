package ca.teamdman.sfm.common.facade;

import ca.teamdman.sfm.common.util.BlockPosSet;
import ca.teamdman.sfm.common.util.ConfirmationParams;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public interface IFacadePlan {
    void apply(World level);
    BlockPosSet positions();
    @Nullable ConfirmationParams computeWarning(World level);
}
