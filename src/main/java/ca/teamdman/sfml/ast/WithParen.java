package ca.teamdman.sfml.ast;

import com.github.bsideup.jabel.Desugar;

import ca.teamdman.sfm.common.resourcetype.ResourceTypeContainer.ResourceType;

@Desugar
public record WithParen(
                        WithClause inner)
        implements ASTNode, WithClause, ToStringPretty {

    @Override
    public <STACK> boolean matchesStack(
                                        ResourceType<STACK, ?, ?> resourceType,
                                        STACK stack) {
        return inner.matchesStack(resourceType, stack);
    }

    @Override
    public String toString() {
        return "(" + inner + ")";
    }
}
