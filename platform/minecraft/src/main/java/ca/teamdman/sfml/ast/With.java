package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.resourcetype.ResourceType;

import java.util.List;

public record With(
        WithClause condition,
        WithMode mode
) implements WithClause, ToStringPretty {
    public static final With ALWAYS_TRUE = new With(
            new WithAlwaysTrue(),
            WithMode.WITH
    );

    @Override
    public <STACK> boolean matchesStack(
            ResourceType<STACK, ?, ?> resourceType,
            STACK stack
    ) {
        boolean matches = condition.matchesStack(resourceType, stack);
        return switch (mode) {
            case WITH -> matches;
            case WITHOUT -> !matches;
        };
    }

    @Override
    public String toString() {
        return switch (mode) {
            case WITH -> "WITH " + condition.toStringPretty();
            case WITHOUT -> "WITHOUT " + condition.toStringPretty();
        };
    }

    @Override
    public List<? extends SfmlAstNode> getChildNodes() {

        return List.of(mode, condition);
    }

    public enum WithMode implements SfmlAstNode {
        WITH,
        WITHOUT;

        @Override
        public List<? extends SfmlAstNode> getChildNodes() {

            return List.of();
        }
    }
}
