package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.resourcetype.ResourceType;

public final class WithAlwaysTrue implements WithClause {
    @Override
    public <ITEM> boolean matchesStack(
            ResourceType<?, ITEM, ?> resourceType,
            ITEM item
    ) {
        return true;
    }

    @Override
    public String toString() {
        return "(ALWAYS => TRUE)";
    }
}
