package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.label.LabelPositionHolder;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public record LabelAccess(
        List<Label> labels,
        SideQualifier sides,
        NumberRangeSet slots,
        RoundRobin roundRobin,
        @Nullable StructAccess structAccess
) implements ASTNode {

    /**
     * Convenience constructor for direct label access (no struct).
     */
    public LabelAccess(
            List<Label> labels,
            SideQualifier sides,
            NumberRangeSet slots,
            RoundRobin roundRobin
    ) {
        this(labels, sides, slots, roundRobin, null);
    }

    /**
     * Returns true if this label access came from a struct USING clause.
     */
    public boolean isStructAccess() {
        return structAccess != null;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();

        // If this is a struct access, show the original form
        if (structAccess != null) {
            builder.append(structAccess);
        } else {
            builder.append(labels.stream().map(Objects::toString).collect(Collectors.joining(", ")));
            if (roundRobin.isEnabled()) {
                builder.append(" ").append(roundRobin);
            }
        }

        if (!sides.equals(SideQualifier.NULL)) {
            builder.append(" ");
            builder
                    .append(sides.sides().stream()
                                    .map(Side::toString)
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
        return builder.toString();
    }

    public ArrayList<Pair<Label, BlockPos>> getLabelledPositions(LabelPositionHolder labelPositionHolder) {
        return roundRobin().getPositionsForLabels(labels(), labelPositionHolder);
    }
}
