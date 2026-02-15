package ca.teamdman.sfm.common.util;

import ca.teamdman.sfm.common.program.LimitedInputSlot;
import ca.teamdman.sfm.common.registry.registration.SFMResourceTypes;
import ca.teamdman.sfm.common.resourcetype.ResourceTypeContainer.ResourceType;
import ca.teamdman.sfml.ast.*;
import ca.teamdman.sfml.ast.Number;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Optional;

public class SFMASTUtils {
    public static <STACK, ITEM, CAP> Optional<InputStatement> getInputStatementForSlot(
            LimitedInputSlot<STACK, ITEM, CAP> slot,
            LabelAccess labelAccess
    ) {
        STACK potential = slot.peekStackInSlot();
        ResourceType<STACK, ITEM, CAP> resourceType = slot.type;
        if (resourceType.isEmpty(potential)) return Optional.empty();
        long toMove = resourceType.getAmount(potential);
        toMove = Long.min(toMove, slot.tracker.getResourceLimit().limit().quantity().number().value());
        long remainingObligation = slot.tracker.getRemainingRetentionObligation(resourceType, potential);
        toMove -= Long.min(toMove, remainingObligation);
        potential = resourceType.withCount(potential, toMove);
        STACK stack = potential;

       return SFMResourceTypes.registry().getKey(resourceType.container)
                .map((ResourceLocation resourceTypeResourceKey) -> getInputStatementForStack(
                        resourceTypeResourceKey,
                        resourceType,
                        stack,
                        "temp",
                        slot.slot,
                        false,
                        null
                ))
                // update the labels
                .map(inputStatement -> new InputStatement(new LabelAccess(
                        labelAccess.labels(),
                        labelAccess.sides(),
                        inputStatement.labelAccess()
                                .slots(),
                        RoundRobin.disabled()
                ), inputStatement.resourceLimits(), inputStatement.each()));
    }

    public static <STACK, ITEM, CAP> InputStatement getInputStatementForStack(
            ResourceLocation resourceTypeResourceKey,
            ResourceType<STACK, ITEM, CAP> resourceType,
            STACK stack,
            String label,
            int slot,
            boolean each,
            @Nullable EnumFacing direction
    ) {
        LabelAccess labelAccess = new LabelAccess(
                Arrays.asList(new Label(label)),
                new SideQualifier(Arrays.asList(Side.fromDirection(direction))),
                new NumberRangeSet(
                        new NumberRange[]{new NumberRange(slot, slot)}
                ),
                RoundRobin.disabled()
        );
        Limit limit = new Limit(
                new ResourceQuantity(
                        new Number(resourceType.getAmount(stack)),
                        ResourceQuantity.IdExpansionBehaviour.NO_EXPAND
                ),
                new ResourceQuantity(
                        new Number(0),
                        ResourceQuantity.IdExpansionBehaviour.NO_EXPAND
                )
        );
       ResourceLimits resourceLimits = ResourceLimits.of(getResourceLimitForStack(
                resourceTypeResourceKey,
                resourceType,
                stack,
                limit
        ));

        return new InputStatement(
                labelAccess,
                resourceLimits,
                each
        );
    }

    public static <STACK, ITEM, CAP> OutputStatement getOutputStatementForStack(
            ResourceLocation resourceTypeResourceKey,
            ResourceType<STACK, ITEM, CAP> resourceType,
            STACK stack,
            String label,
            int slot,
            boolean each,
            @Nullable EnumFacing direction
    ) {
        LabelAccess labelAccess = new LabelAccess(
                Arrays.asList(new Label(label)),
                new SideQualifier(Arrays.asList(Side.fromDirection(direction))),
                new NumberRangeSet(
                        new NumberRange[]{new NumberRange(slot, slot)}
                ),
                RoundRobin.disabled()
        );

        ResourceLimits resourceLimits = ResourceLimits.of(getResourceLimitForStack(
                resourceTypeResourceKey,
                resourceType,
                stack,
                Limit.MAX_QUANTITY_MAX_RETENTION
        ));

        return new OutputStatement(
                labelAccess,
                resourceLimits,
                each,
                false
        );
    }

    public static <STACK, ITEM, CAP> ResourceLimit getResourceLimitForStack(
            ResourceLocation resourceTypeResourceKey,
            ResourceType<STACK, ITEM, CAP> resourceType,
            STACK stack,
            Limit limit
    ) {
        ResourceLocation stackId = resourceType.getRegistryKeyForStack(stack);
        ResourceIdentifier<STACK, ITEM, CAP> resourceIdentifier = new ResourceIdentifier<>(
                resourceTypeResourceKey,
                stackId
        );

        var meta = resourceType.getMetaForStack(stack);
        return new ResourceLimit(
                new ResourceIdSet(Arrays.asList(resourceIdentifier)),
                limit,
                meta.isPresent() && meta.get() != 0 ? With.meta(meta.get()) : With.ALWAYS_TRUE
        );
    }
}
