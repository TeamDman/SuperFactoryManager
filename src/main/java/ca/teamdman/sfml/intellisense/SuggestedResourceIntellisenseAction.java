package ca.teamdman.sfml.intellisense;

import ca.teamdman.sfm.common.registry.SFMResourceTypes;
import ca.teamdman.sfm.common.resourcetype.ResourceTypeContainer.ResourceType;
import ca.teamdman.sfml.ast.ResourceIdentifier;
import ca.teamdman.sfml.manipulation.ManipulationResult;
import com.github.bsideup.jabel.Desugar;
import net.minecraft.util.text.TextComponentString;

import java.util.Objects;

@Desugar
public record SuggestedResourceIntellisenseAction<STACK, ITEM, CAP>(
        ResourceType<STACK, ITEM, CAP> resourceType,
        ITEM item,
        TextComponentString display
) implements IntellisenseAction {
    public SuggestedResourceIntellisenseAction(
            ResourceType<STACK, ITEM, CAP> resourceType,
            ITEM item
    ) {
        this(
                resourceType,
                item,
                new TextComponentString(
                        new ResourceIdentifier<>(
                                Objects.requireNonNull(SFMResourceTypes.registry().getId(resourceType.container)),
                                resourceType.getRegistryKeyForItem(item)
                        ).toStringCondensed()
                )
        );
    }

    @Override
    public TextComponentString getComponent() {
        return display();
    }

    @Override
    public ManipulationResult perform(IntellisenseContext context) {
        return context
                .createMutableProgramString()
                .replaceWordAndMoveCursorsToEnd(String.format("%s ", display().getText()))
                .intoResult();
    }
}
