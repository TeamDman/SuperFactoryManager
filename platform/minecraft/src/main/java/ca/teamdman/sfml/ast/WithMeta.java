package ca.teamdman.sfml.ast;

import com.github.bsideup.jabel.Desugar;

import ca.teamdman.sfm.common.resourcetype.ResourceTypeContainer.ResourceType;

@Desugar
public record WithMeta(Number number) implements ASTNode, WithClause, ToStringPretty {

    @Override
    public <STACK> boolean matchesStack(
            ResourceType<STACK, ?, ?> resourceType,
            STACK stack
    ) {
        return resourceType.getMetaForStack(stack).map((number) -> number == this.number.value()).orElse(false);
    }

    @Override
    public boolean hasShorthand() {
        return true;
    }

    @Override
    public String toStringCondensed() {
        return "@" + number;
    }

    @Override
    public String toString() {
        return "META " + number;
    }
}
