package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.resourcetype.ResourceType;

public record WithParen(WithClause inner) implements ASTNode, WithClause, ToStringPretty {
    @Override
    public <STACK> boolean matchesStack(
            ResourceType<STACK, ?, ?> resourceType,
            STACK stack
    ) {
        return inner.matchesStack(resourceType, stack);
    }

    @Override
    public String toString() {
        return "(" + inner + ")";
    }
}
