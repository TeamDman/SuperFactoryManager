package ca.teamdman.sfm.common.program.linting;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfml.ast.Program;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.registries.IForgeRegistryEntry;
import org.jetbrains.annotations.Nullable;

public interface IProgramLinter extends IForgeRegistryEntry<IProgramLinter>  {
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
            World level,
            ItemStack disk
    );
    /*
    todo:

        // update warnings on the disk itself
        var updatedWarnings = gatherWarnings(program, labels, manager, tracker);
        DiskItem.setWarnings(disk, updatedWarnings);
     */
}
