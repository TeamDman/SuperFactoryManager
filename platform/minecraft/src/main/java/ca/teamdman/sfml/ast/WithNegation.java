package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.resourcetype.ResourceType;

public record WithNegation(WithClause inner) implements ASTNode, WithClause, ToStringPretty {
    @Override
    public <ITEM> boolean matchesStack(
            ResourceType<?, ITEM, ?> resourceType,
            ITEM item
    ) {
        return !inner.matchesStack(resourceType, item);
    }

    @Override
    public String toString() {
        return "NOT " + inner;
    }
}
