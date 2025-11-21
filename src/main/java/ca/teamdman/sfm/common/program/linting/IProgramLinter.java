package ca.teamdman.sfm.common.program.linting;

import java.util.ArrayList;

import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.registries.IForgeRegistryEntry;

import org.jetbrains.annotations.Nullable;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfml.ast.Program;

public interface IProgramLinter extends IForgeRegistryEntry<IProgramLinter> {

    abstract ArrayList<TextComponentTranslation> gatherWarnings(
                                                                Program program,
                                                                LabelPositionHolder labelPositionHolder,
                                                                @Nullable ManagerBlockEntity managerBlockEntity);

    abstract void fixWarnings(
                              ManagerBlockEntity managerBlockEntity,
                              ItemStack diskStack,
                              Program program);
}
