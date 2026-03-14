package ca.teamdman.sfm.common.program.linting;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.util.SFMEnvironmentUtils;
import ca.teamdman.sfml.ast.SFMLProgram;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public class ForgetStatementNoMatchesProgramLinter implements IProgramLinter{
    @Override
    public void gatherWarnings(
            SFMLProgram program,
            LabelPositionHolder labelPositionHolder,
            @Nullable ManagerBlockEntity managerBlockEntity,
            ProblemTracker tracker
    ) {
        if (SFMEnvironmentUtils.isInIDE()) {
            SFM.LOGGER.warn("TODO: check if a forget statement has a label expression that no previous input statements introduce.");
        }
    }

    @Override
    public void fixWarnings(
            SFMLProgram program,
            LabelPositionHolder labels,
            ManagerBlockEntity manager,
            Level level,
            ItemStack disk
    ) {

    }

}
