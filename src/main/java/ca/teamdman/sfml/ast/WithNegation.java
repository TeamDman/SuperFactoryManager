package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.resourcetype.ResourceTypeContainer;
import ca.teamdman.sfm.common.resourcetype.ResourceTypeContainer.ResourceType;
import com.github.bsideup.jabel.Desugar;

@Desugar public record WithNegation(
        WithClause inner
) implements ASTNode, WithClause, ToStringPretty {
    @Override
    public <STACK> boolean matchesStack(
            ResourceType<STACK, ?, ?> resourceType,
            STACK stack
    ) {
        return !inner.matchesStack(resourceType, stack);
    }

    @Override
    public String toString() {
        return "NOT " + inner;
    }
}
