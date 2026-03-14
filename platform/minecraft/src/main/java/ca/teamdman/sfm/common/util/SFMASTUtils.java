package ca.teamdman.sfm.common.util;

import ca.teamdman.sfm.common.program.LimitedInputSlot;
import ca.teamdman.sfm.common.registry.SFMResourceTypes;
import ca.teamdman.sfm.common.resourcetype.ResourceType;
import ca.teamdman.sfml.ast.*;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

public class SFMASTUtils {
    public static <STACK, ITEM, CAP> Optional<InputStatement> getInputStatementForSlot(
            LimitedInputSlot<STACK, ITEM, CAP> slot,
            ResourceAccess resourceAccess
    ) {

        STACK potential = slot.peekExtractPotential();
        ResourceType<STACK, ITEM, CAP> resourceType = slot.type;
        if (resourceType.isEmpty(potential)) return Optional.empty();
        long toMove = resourceType.getAmount(potential);
        toMove = Long.min(toMove, slot.tracker.getResourceLimit().limit().quantity().number().value());
        long remainingObligation = slot.tracker.getRemainingRetentionObligation(resourceType, potential);
        toMove -= Long.min(toMove, remainingObligation);
        potential = resourceType.withCount(potential, toMove);
        STACK stack = potential;

        return SFMResourceTypes.registry().getKey(resourceType)
                .map(x -> {
                    //noinspection unchecked,rawtypes
                    return (ResourceKey<ResourceType<STACK, ITEM, CAP>>) (ResourceKey) x;
                })
                .map((ResourceKey<ResourceType<STACK, ITEM, CAP>> resourceTypeResourceKey) -> getInputStatementForStack(
                        resourceTypeResourceKey,
                        resourceType,
                        stack,
                        "temp",
                        slot.slot,
                        false,
                        null
                ))
                // update the labels
                .map(inputStatement -> new InputStatement(
                        new ResourceAccess(
                                resourceAccess.labelExpressions(),
                                new RoundRobin(RoundRobinBehaviour.UNMODIFIED), resourceAccess.sides(),
                                inputStatement.resourceAccess()
                                        .slots()
                        ), inputStatement.resourceLimits(), inputStatement.each()
                ));
    }

    public static <STACK, ITEM, CAP> InputStatement getInputStatementForStack(
            ResourceKey<ResourceType<STACK, ITEM, CAP>> resourceTypeResourceKey,
            ResourceType<STACK, ITEM, CAP> resourceType,
            STACK stack,
            String label,
            int slot,
            boolean each,
            @Nullable Direction direction
    ) {

        ResourceAccess resourceAccess = new ResourceAccess(
                List.of(new LabelExpressionSingle(new Label(label))),
                new RoundRobin(RoundRobinBehaviour.UNMODIFIED), new SideQualifier(List.of(Side.fromDirection(direction))),
                new SlotQualifier(false, NumberSet.of(slot))
        );
        Limit limit = new Limit(
                new ResourceQuantity(
                        ResourceQuantity.IdExpansionBehaviour.NO_EXPAND,
                        NumberExpression.fromLiteral(resourceType.getAmount(stack))
                ),
                new ResourceQuantity(
                        ResourceQuantity.IdExpansionBehaviour.NO_EXPAND, NumberExpression.fromLiteral(0)
                )
        );
        ResourceLocation stackId = resourceType.getRegistryKeyForStack(stack);
        ResourceIdentifier<STACK, ITEM, CAP> resourceIdentifier = new ResourceIdentifier<>(
                resourceTypeResourceKey,
                stackId
        );
        ResourceLimit resourceLimit = new ResourceLimit(
                new ResourceIdSet(List.of(resourceIdentifier)),
                limit,
                With.ALWAYS_TRUE
        );
        ResourceLimits resourceLimits = new ResourceLimits(
                List.of(resourceLimit),
                ResourceIdSet.EMPTY
        );

        // todo: add WITH logic here to also build code to match any item/block tags present
        return new InputStatement(
                resourceAccess,
                resourceLimits,
                each
        );
    }

}
