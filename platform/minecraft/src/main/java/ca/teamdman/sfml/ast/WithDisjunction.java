package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.resourcetype.ResourceType;

public record WithDisjunction(WithClause left, WithClause right) implements ASTNode, WithClause, ToStringPretty {
    @Override
    public <ITEM> boolean matchesStack(
            ResourceType<?, ITEM, ?> resourceType,
            ITEM item
    ) {
        return left.matchesStack(resourceType, item) || right.matchesStack(resourceType, item);
    }

    @Override
    public String toString() {
        return left + " OR " + right;
    }
}
