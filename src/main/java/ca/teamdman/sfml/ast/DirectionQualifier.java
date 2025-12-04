package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.util.SFMDirections;
import com.github.bsideup.jabel.Desugar;
import net.minecraft.util.EnumFacing;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.stream.Stream;

import static net.minecraft.util.EnumFacing.*;

@Desugar public record DirectionQualifier(
        EnumSet<EnumFacing> directions
) implements ASTNode, Iterable<EnumFacing> {

    public static final DirectionQualifier NULL_DIRECTION = new DirectionQualifier(EnumSet.noneOf(EnumFacing.class));
    public static final DirectionQualifier EVERY_DIRECTION = new DirectionQualifier(EnumSet.allOf(EnumFacing.class));

    public static EnumFacing lookup(Side side) {
        return switch (side) {
            case TOP -> UP;
            case BOTTOM -> DOWN;
            case NORTH -> NORTH;
            case SOUTH -> SOUTH;
            case EAST -> EAST;
            case WEST -> WEST;
        };
    }

    public static String directionToString(@Nullable EnumFacing direction) {
        if (direction == null) return "";
        return switch (direction) {
            case UP -> "TOP";
            case DOWN -> "BOTTOM";
            case NORTH -> "NORTH";
            case SOUTH -> "SOUTH";
            case EAST -> "EAST";
            case WEST -> "WEST";
        };
    }

    public Stream<EnumFacing> stream() {
        if (this == EVERY_DIRECTION)
            return Stream.concat(directions.stream(), Stream.<EnumFacing>builder().add(null).build());
        if (directions.isEmpty()) return Stream.<EnumFacing>builder().add(null).build();
        return directions.stream();
    }

    @Override
    @NotNull
    public Iterator<EnumFacing> iterator() {
        if (this == EVERY_DIRECTION) {
            return new SFMDirections.NullableDirectionIterator();
        }
        if (directions.isEmpty()) {
            return new SFMDirections.SingleNullDirectionIterator();
        }
        // Return the iterator of the original collection directly.
        return directions.iterator();
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DirectionQualifier that)) return false;
        return Objects.equals(directions, that.directions);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(directions);
    }

}
