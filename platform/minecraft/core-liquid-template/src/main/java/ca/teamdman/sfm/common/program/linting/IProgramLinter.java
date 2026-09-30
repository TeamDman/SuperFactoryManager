package ca.teamdman.sfm.common.program.linting;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfml.ast.Program;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public interface IProgramLinter {
    void gatherWarnings(
            Program program,
            LabelPositionHolder labelPositionHolder,
            @Nullable ManagerBlockEntity managerBlockEntity,
            ProblemTracker tracker
    );

    /// This method can update the disk program but should not modify the warnings since we will
    /// recompute the warnings after fixing.
    void fixWarnings(
            Program program,
            LabelPositionHolder labels,
            ManagerBlockEntity manager,
            Level level,
            ItemStack disk
    );
    /*
    todo:

        // update warnings on the disk itself
        var updatedWarnings = gatherWarnings(program, labels, manager, tracker);
        DiskItem.setWarnings(disk, updatedWarnings);
     */
}
