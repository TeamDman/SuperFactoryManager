package ca.teamdman.sfml.ast;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record SideQualifier(List<Side> sides) implements ASTNode {
    public static final SideQualifier NULL = new SideQualifier(List.of(Side.NULL));

    public static final SideQualifier ALL = new SideQualifier(List.of(
            Side.TOP, Side.BOTTOM, Side.NORTH, Side.SOUTH, Side.EAST, Side.WEST, Side.NULL
    ));

    public static final SideQualifier DEFAULT = NULL;

    public @Nullable Direction getNonNullDirection(BlockState blockState) {
        for (Side side : sides) {
            if (side != Side.NULL) {
                return side.resolve(blockState);
            }
        }
        return null;
    }

    @Override
    public boolean equals(Object o) {

        if (this == o) return true;
        if (!(o instanceof SideQualifier that)) return false;
        return Objects.equals(sides, that.sides);
    }

    @Override
    public int hashCode() {

        return Objects.hashCode(sides);
    }

    public ArrayList<@Nullable Direction> resolve(BlockState blockState) {
        ArrayList<@Nullable Direction> rtn = new ArrayList<>(7);
        for (Side side : sides) {
            rtn.add(side.resolve(blockState));
        }
        return rtn;
    }

}
