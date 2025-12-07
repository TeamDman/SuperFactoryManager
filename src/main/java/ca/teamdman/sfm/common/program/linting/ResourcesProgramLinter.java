package ca.teamdman.sfm.common.program.linting;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.resourcetype.ResourceTypeContainer;
import ca.teamdman.sfml.ast.Program;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.registries.IForgeRegistryEntry;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

import static ca.teamdman.sfm.common.localization.LocalizationKeys.PROGRAM_WARNING_UNKNOWN_RESOURCE_ID;

public class ResourcesProgramLinter extends IForgeRegistryEntry.Impl<IProgramLinter> implements IProgramLinter {

    @Override
    public void gatherWarnings(
            Program program,
            LabelPositionHolder labelPositionHolder,
            @Nullable ManagerBlockEntity managerBlockEntity,
            ProblemTracker tracker
    ) {
        // Check all referenced resources to see if they exist
        for (var resource : program.referencedResources()) {
            Optional<?> loc = resource.getLocation();
            if (!loc.isPresent()) {
                // It's a pattern-based resource or something not requiring a registry check
                continue;
            }
            // resource.getResourceType() can return null if something's not mapped
            ResourceTypeContainer. ResourceType<?, ?, ?> resourceType = resource.getResourceType();
            if (resourceType == null) {
                continue;
            }
            // If it doesn't exist in the registry, add a warning
            if (!resourceType.registryKeyExists((ResourceLocation) loc.get())) {
                if (tracker.add(PROGRAM_WARNING_UNKNOWN_RESOURCE_ID.get(resource)).isSaturated()) {
                    break;
                };
            }
        }
    }

    @Override
    public void fixWarnings(
            Program program,
            LabelPositionHolder labels,
            ManagerBlockEntity manager,
            World level,
            ItemStack disk
    ) {
        // Resource references typically cannot be “auto-fixed,” so do nothing here.
    }
}
