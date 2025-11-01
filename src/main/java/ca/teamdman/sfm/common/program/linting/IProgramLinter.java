package ca.teamdman.sfm.common.program.linting;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfml.ast.Program;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

public interface IProgramLinter {
    ArrayList<TextComponentTranslation> gatherWarnings(
            Program program,
            LabelPositionHolder labelPositionHolder,
            @Nullable
            ManagerBlockEntity managerBlockEntity
    );

    void fixWarnings(
            ManagerBlockEntity managerBlockEntity,
            ItemStack diskStack,
            Program program
    );
}
