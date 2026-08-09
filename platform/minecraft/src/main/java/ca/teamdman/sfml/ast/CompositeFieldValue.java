package ca.teamdman.sfml.ast;

/**
 * Represents a composite struct field value that combines side and slot qualifiers.
 * Example: TOP SIDE SLOTS 0
 */
public record CompositeFieldValue(
        SideQualifier sides,
        NumberRangeSet slots
) implements StructFieldValue {
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (!sides.equals(SideQualifier.NULL)) {
            sb.append(sides.sides().stream()
                    .map(Side::toString)
                    .reduce((a, b) -> a + ", " + b)
                    .orElse(""));
            sb.append(" SIDE");
        }
        if (!slots.equals(NumberRangeSet.MAX_RANGE)) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append("SLOTS ").append(slots);
        }
        return sb.toString();
    }
}
