package ca.teamdman.sfm.common.program.linting;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.cablenetwork.CableNetworkManager;
import ca.teamdman.sfm.common.capability.SFMBlockCapabilityDiscovery;
import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfml.ast.Program;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.item.ItemStack;
import net.minecraftforge.registries.IForgeRegistryEntry;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

import static ca.teamdman.sfm.common.localization.LocalizationKeys.*;

public class LabelLinter extends IForgeRegistryEntry.Impl<IProgramLinter> implements IProgramLinter {

    @Override
    public ArrayList<TextComponentTranslation> gatherWarnings(
            Program program,
            LabelPositionHolder labelPositionHolder,
            @Nullable ManagerBlockEntity managerBlockEntity
    ) {
        ArrayList<TextComponentTranslation> warnings = new ArrayList<>();

        addWarningsForLabelsInProgramButNotInHolder(program, labelPositionHolder, warnings);
        addWarningsForLabelsInHolderButNotInProgram(program, labelPositionHolder, warnings);

        if (managerBlockEntity != null && managerBlockEntity.getLevel() != null) {
            addWarningsForLabelsUsedInWorldButNotConnectedByCables(
                    managerBlockEntity,
                    labelPositionHolder,
                    warnings,
                    managerBlockEntity.getLevel()
            );
        }

        // If we added label warnings, add the reminder to push labels
        // if we found any new warnings related to labels
        if (!warnings.isEmpty()) {
            warnings.add(PROGRAM_REMINDER_PUSH_LABELS.get());
        }

        return warnings;
    }

    @Override
    public void fixWarnings(
            ManagerBlockEntity managerBlockEntity,
            ItemStack diskStack,
            Program program
    ) {
        if (managerBlockEntity == null || managerBlockEntity.getLevel() == null) {
            return;
        }
        fixWarningsByRemovingBadLabelsFromDisk(managerBlockEntity, diskStack, program);
    }

    // ------------------------------------------
    // PRIVATE METHODS
    // ------------------------------------------

    private void addWarningsForLabelsInProgramButNotInHolder(
            Program program,
            LabelPositionHolder labels,
            ArrayList<TextComponentTranslation> warnings
    ) {
        for (String label : program.referencedLabels()) {
            var isUsed = !labels.getPositions(label).isEmpty();
            if (!isUsed) {
                warnings.add(PROGRAM_WARNING_UNUSED_LABEL.get(label));
            }
        }
    }

    private void addWarningsForLabelsInHolderButNotInProgram(
            Program program,
            LabelPositionHolder labels,
            ArrayList<TextComponentTranslation> warnings
    ) {
        labels.labels()
                .keySet()
                .stream()
                .filter(x -> !program.referencedLabels().contains(x))
                .forEach(label -> warnings.add(PROGRAM_WARNING_UNDEFINED_LABEL.get(label)));
    }

    private void addWarningsForLabelsUsedInWorldButNotConnectedByCables(
            ManagerBlockEntity manager,
            LabelPositionHolder labels,
            ArrayList<TextComponentTranslation> warnings,
            Level level
    ) {
        CableNetworkManager
                .getOrRegisterNetworkFromManagerPosition(manager)
                .ifPresent(network -> labels.forEach((label, pos) -> {
                    var adjacent = network.isAdjacentToCable(pos);
                    if (!adjacent) {
                        warnings.add(PROGRAM_WARNING_DISCONNECTED_LABEL.get(
                                label,
                                String.format("[%d,%d,%d]", pos.getX(), pos.getY(), pos.getZ())
                        ));
                    }
                    var viable = SFMBlockCapabilityDiscovery.hasAnyCapabilityAnyDirection(level, pos);
                    if (!viable && adjacent) {
                        warnings.add(PROGRAM_WARNING_CONNECTED_BUT_NOT_VIABLE_LABEL.get(
                                label,
                                String.format("[%d,%d,%d]", pos.getX(), pos.getY(), pos.getZ())
                        ));
                    }
                }));
    }

    private void fixWarningsByRemovingBadLabelsFromDisk(
            ManagerBlockEntity manager,
            ItemStack disk,
            Program program
    ) {
        var labels = LabelPositionHolder.from(disk);
        // remove labels not defined in code
        labels.removeIf(label -> !program.referencedLabels().contains(label));

        // remove labels not connected via cables
        CableNetworkManager
                .getOrRegisterNetworkFromManagerPosition(manager)
                .ifPresent(network -> labels.removeIf((label, pos) -> !network.isAdjacentToCable(pos)));

        // remove labels with no viable capability provider
        var level = manager.getLevel();
        labels.removeIf((label, pos) -> !SFMBlockCapabilityDiscovery.hasAnyCapabilityAnyDirection(level, pos));

        // save new labels
        labels.save(disk);

        // update warnings on the disk itself
        var updatedWarnings = gatherWarnings(program, labels, manager);
        DiskItem.setWarnings(disk, updatedWarnings);
    }
}
