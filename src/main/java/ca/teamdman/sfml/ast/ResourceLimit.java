package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.program.*;
import ca.teamdman.sfm.common.resourcetype.ResourceType;

public record ResourceLimit(
        ResourceIdSet resourceIds,
        Limit limit,
        With with
) implements ASTNode {
    public static final ResourceLimit TAKE_ALL_LEAVE_NONE = new ResourceLimit(
            ResourceIdSet.MATCH_ALL,
            Limit.MAX_QUANTITY_NO_RETENTION,
            With.ALWAYS_TRUE
    );
    public static final ResourceLimit ACCEPT_ALL_WITHOUT_RESTRAINT = new ResourceLimit(
            ResourceIdSet.MATCH_ALL,
            Limit.MAX_QUANTITY_MAX_RETENTION,
            With.ALWAYS_TRUE
    );

    public ResourceLimit withDefaultLimit(Limit defaults) {
        return new ResourceLimit(resourceIds, limit.withDefaults(defaults), with);
    }

    public ResourceLimit withLimit(Limit limit) {
        return new ResourceLimit(resourceIds, limit, with);
    }

    public IInputResourceTracker createInputTracker(
            ResourceIdSet exclusions
    ) {
        return switch (limit.quantity().idExpansionBehaviour()) {
            case EXPAND -> switch (limit.retention().idExpansionBehaviour()) {
                case EXPAND -> new ExpandedQuantityExpandedRetentionInputResourceTracker(this, exclusions);
                case NO_EXPAND -> new ExpandedQuantitySharedRetentionInputResourceTracker(this, exclusions);
            };
            case NO_EXPAND -> switch (limit.retention().idExpansionBehaviour()) {
                case EXPAND -> new SharedQuantityExpandedRetentionInputResourceTracker(this, exclusions);
                case NO_EXPAND -> new SharedQuantitySharedRetentionInputResourceTracker(this, exclusions);
            };
        };
    }

    public IOutputResourceTracker createOutputTracker(
            ResourceIdSet exclusions
    ) {
        return switch (limit.quantity().idExpansionBehaviour()) {
            case EXPAND -> switch (limit.retention().idExpansionBehaviour()) {
                case EXPAND -> new ExpandedQuantityExpandedRetentionOutputResourceTracker(this, exclusions);
                case NO_EXPAND -> new ExpandedQuantitySharedRetentionOutputResourceTracker(this, exclusions);
            };
            case NO_EXPAND -> switch (limit.retention().idExpansionBehaviour()) {
                case EXPAND -> new SharedQuantityExpandedRetentionOutputResourceTracker(this, exclusions);
                case NO_EXPAND -> new SharedQuantitySharedRetentionOutputResourceTracker(this, exclusions);
            };
        };
    }

    public boolean matchesStack(Object stack) {
        var matchingIdPattern = resourceIds.getMatchingFromStack(stack);
        if (matchingIdPattern == null) {
            return false;
        }
        @SuppressWarnings("unchecked")
        ResourceType<Object, ?, ?> resourceType = (ResourceType<Object, ?, ?>) matchingIdPattern.getResourceType();
        if (resourceType == null) {
            return false;
        }
        return with.matchesStack(resourceType, stack);
    }

    @Override
    public String toString() {
        return limit + " " + resourceIds + (with == With.ALWAYS_TRUE ? "" : " WITH " + with);
    }

    public String toStringCondensed(Limit defaults) {
        return (
                limit.toStringCondensed(defaults) + " " + resourceIds.toStringCondensed() + (
                        with == With.ALWAYS_TRUE
                        ? ""
                        : " WITH " + with
                )
        ).trim();
    }
}
