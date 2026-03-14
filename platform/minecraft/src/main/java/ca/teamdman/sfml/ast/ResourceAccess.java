package ca.teamdman.sfml.ast;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public record ResourceAccess(
        List<LabelExpression> labelExpressions,

        RoundRobin roundRobin,

        SideQualifier sides,

        SlotQualifier slots
) implements SfmlAstNode {
    @Override
    public String toString() {

        StringBuilder builder = new StringBuilder();
        builder.append(labelExpressions.stream().map(Objects::toString).collect(Collectors.joining(", ")));
        if (roundRobin.isModified()) {
            builder.append(" ").append(roundRobin);
        }
        if (!sides.equals(SideQualifier.NULL)) {
            builder.append(" ");
            builder
                    .append(sides.sides().stream()
                                    .map(Side::toString)
                                    .collect(Collectors.joining(", ")))
                    .append(" SIDE");
        }
        if (!slots.isDefault()) {
            builder.append(slots);
        }
        return builder.toString();
    }

    @Override
    public List<? extends SfmlAstNode> getChildNodes() {

        ArrayList<SfmlAstNode> rtn = new ArrayList<>(labelExpressions.size() + 3);
        rtn.addAll(labelExpressions);
        rtn.add(roundRobin);
        rtn.add(sides);
        rtn.add(slots);
        return rtn;
    }

}
