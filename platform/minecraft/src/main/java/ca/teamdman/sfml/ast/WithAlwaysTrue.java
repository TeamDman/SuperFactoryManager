package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.resourcetype.ResourceTypeContainer.ResourceType;

public final class WithAlwaysTrue implements WithClause {
    @Override
    public <STACK> boolean matchesStack(
            ResourceType<STACK, ?, ?> resourceType,
            STACK stack
    ) {
        return true;
    }

    @Override
    public boolean hasShorthand() {
        return true;
    }

    @Override
    public String toStringCondensed() {
        return "";
    }

    @Override
    public String toString() {
        return "(ALWAYS => TRUE)";
    }
}
