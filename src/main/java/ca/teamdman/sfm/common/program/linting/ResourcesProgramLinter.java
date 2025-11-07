package ca.teamdman.sfm.common.program.linting;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfml.ast.Program;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.ResourceLocation;
import net.minecraft.item.ItemStack;
import net.minecraftforge.registries.IForgeRegistryEntry;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Optional;

import static ca.teamdman.sfm.common.localization.LocalizationKeys.PROGRAM_WARNING_UNKNOWN_RESOURCE_ID;

public class ResourcesProgramLinter extends IForgeRegistryEntry.Impl<IProgramLinter> implements IProgramLinter {

    @Override
    public ArrayList<TextComponentTranslation> gatherWarnings(
            Program program,
            LabelPositionHolder labelPositionHolder,
            @Nullable ManagerBlockEntity managerBlockEntity
    ) {
        ArrayList<TextComponentTranslation> warnings = new ArrayList<>();

        // Check all referenced resources to see if they exist
        for (var resource : program.referencedResources()) {
            Optional<?> loc = resource.getLocation();
            if (loc.isEmpty()) {
                // It's a pattern-based resource or something not requiring a registry check
                continue;
            }
            // resource.getResourceType() can return null if something's not mapped
            if (resource.getResourceType() == null) {
                continue;
            }
            // If it doesn't exist in the registry, add a warning
            if (!resource.getResourceType().registryKeyExists((ResourceLocation) loc.get())) {
                warnings.add(PROGRAM_WARNING_UNKNOWN_RESOURCE_ID.get(resource));
            }
        }

        return warnings;
    }

    @Override
    public void fixWarnings(
            ManagerBlockEntity managerBlockEntity,
            ItemStack diskStack,
            Program program
    ) {
        // Resource references typically cannot be “auto-fixed,” so do nothing here.
    }
}
