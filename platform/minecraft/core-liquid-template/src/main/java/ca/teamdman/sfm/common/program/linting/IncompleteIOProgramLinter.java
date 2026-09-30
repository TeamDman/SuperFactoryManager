package ca.teamdman.sfm.common.program.linting;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.program.ProgramContext;
import ca.teamdman.sfml.ast.Program;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public class IncompleteIOProgramLinter implements IProgramLinter {
    /// Ensure we have both input and output if needed
    @Override
    public void gatherWarnings(
            Program program,
            LabelPositionHolder labelPositionHolder,
            @Nullable ManagerBlockEntity managerBlockEntity,
            ProblemTracker tracker
    ) {

        program.tick(
                ProgramContext.createSimulationContext(
                        program,
                        labelPositionHolder,
                        0,
                        new GatherWarningsProgramBehaviour(tracker)
                )
        );
    }

    @Override
    public void fixWarnings(
            Program program,
            LabelPositionHolder labels,
            ManagerBlockEntity manager,
            Level level,
            ItemStack disk
    ) {

    }

}
