package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.util.SFMDirections;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.stream.Stream;

public record DirectionQualifier(EnumSet<Direction> directions) implements ASTNode, Iterable<Direction> {

    public static final DirectionQualifier NULL_DIRECTION = new DirectionQualifier(EnumSet.noneOf(Direction.class));
    public static final DirectionQualifier EVERY_DIRECTION = new DirectionQualifier(EnumSet.allOf(Direction.class));

    public static Direction lookup(Side side) {
        return switch (side) {
            case TOP -> Direction.UP;
            case BOTTOM -> Direction.DOWN;
            case NORTH -> Direction.NORTH;
            case SOUTH -> Direction.SOUTH;
            case EAST -> Direction.EAST;
            case WEST -> Direction.WEST;
        };
    }

    public static String directionToString(@Nullable Direction direction) {
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

    public Stream<Direction> stream() {
        if (this == EVERY_DIRECTION)
            return Stream.concat(directions.stream(), Stream.<Direction>builder().add(null).build());
        if (directions.isEmpty()) return Stream.<Direction>builder().add(null).build();
        return directions.stream();
    }

    @Override
    public Iterator<@Nullable Direction> iterator() {
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
