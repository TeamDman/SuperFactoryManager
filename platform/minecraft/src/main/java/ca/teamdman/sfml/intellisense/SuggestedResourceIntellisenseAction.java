package ca.teamdman.sfml.intellisense;

import ca.teamdman.sfm.common.registry.SFMResourceTypes;
import ca.teamdman.sfm.common.resourcetype.ResourceType;
import ca.teamdman.sfml.ast.ResourceIdentifier;
import ca.teamdman.sfml.manipulation.ManipulationResult;
import net.minecraft.network.chat.Component;

import java.util.Objects;

public record SuggestedResourceIntellisenseAction<STACK, ITEM, CAP>(
        ResourceType<STACK, ITEM, CAP> resourceType,
        ITEM item,
        Component display
) implements IntellisenseAction {
    public SuggestedResourceIntellisenseAction(
            ResourceType<STACK, ITEM, CAP> resourceType,
            ITEM item
    ) {
        this(
                resourceType,
                item,
                Component.literal(
                        new ResourceIdentifier<>(
                                Objects.requireNonNull(SFMResourceTypes.registry().getId(resourceType)),
                                resourceType.getRegistryKeyForItem(item)
                        ).toStringCondensed()
                )
        );
    }

    @Override
    public Component getComponent() {
        return display();
    }

    @Override
    public ManipulationResult perform(IntellisenseContext context) {
        return context
                .createMutableProgramString()
                .replaceWordAndMoveCursorsToEnd("%s ".formatted(display().getString()))
                .intoResult();
    }
}
