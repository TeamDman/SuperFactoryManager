package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.resourcetype.ResourceType;

import java.util.List;

public record WithParen(WithClause inner) implements SfmlAstNode, WithClause, ToStringPretty {
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

    @Override
    public List<WithClause> getChildNodes() {

        return List.of(inner);
    }

}
