package ca.teamdman.sfm.common.facade;

import ca.teamdman.sfm.common.util.BlockPosSet;
import ca.teamdman.sfm.common.util.ConfirmationParams;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public interface IFacadePlan {
    void apply(Level level);
    BlockPosSet positions();
    @Nullable ConfirmationParams computeWarning(Level level);
}
