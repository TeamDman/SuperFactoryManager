package ca.teamdman.sfml.ast;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public record LabelAccess(
        List<Label> labels,
        DirectionQualifier directions,
        NumberRangeSet slots,
        RoundRobin roundRobin,
        WhereStatement where
) implements ASTNode {
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(labels.stream().map(Objects::toString).collect(Collectors.joining(", ")));
        if (roundRobin.isEnabled()) {
            builder.append(" ").append(roundRobin);
        }
        if (!directions.directions().isEmpty()) {
            builder.append(" ");
            builder
                    .append(directions
                                    .stream()
                                    .map(DirectionQualifier::directionToString)
                                    .collect(Collectors.joining(", ")))
                    .append(" SIDE");
        }
        if (slots.ranges().length > 0) {
            if (slots.ranges().length != 1 || !slots.ranges()[0].equals(NumberRange.MAX_RANGE)) {
                builder.append(" SLOTS");
                for (NumberRange range : slots.ranges()) {
                    builder.append(" ").append(range);
                }
            }
        }
        builder.append(where);
        return builder.toString();
    }
}
