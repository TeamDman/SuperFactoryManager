package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.resourcetype.ResourceTypeContainer.ResourceType;
import com.github.bsideup.jabel.Desugar;


@Desugar public record With(
        WithClause condition,
        WithMode mode
) implements WithClause, ToStringPretty {
    public static final With ALWAYS_TRUE = new With(
            new WithAlwaysTrue(),
            WithMode.WITH
    );

    public static With meta(long metadata) {
        return new With(new WithMeta(new Number(metadata)), WithMode.WITH);
    }

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

    public String toStringCondensed() {
        if (mode == WithMode.WITH && condition.hasShorthand()) return condition.toStringCondensed();
        return switch (mode) {
            case WITH -> " WITH " + condition.toStringPretty();
            case WITHOUT -> " WITHOUT " + condition.toStringPretty();
        };
    }

    @Override
    public String toString() {
        return switch (mode) {
            case WITH -> "WITH " + condition.toStringPretty();
            case WITHOUT -> "WITHOUT " + condition.toStringPretty();
        };
    }

    public enum WithMode {
        WITH,
        WITHOUT
    }
}
