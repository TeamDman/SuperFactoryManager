package ca.teamdman.sfml.ast;

import com.github.bsideup.jabel.Desugar;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Desugar
public record SideQualifier(List<Side> sides) implements ASTNode {
    public static final SideQualifier NULL = new SideQualifier(Arrays.asList(Side.NULL));

    public static final SideQualifier ALL = new SideQualifier(Arrays.asList(
            Side.TOP, Side.BOTTOM, Side.NORTH, Side.SOUTH, Side.EAST, Side.WEST, Side.NULL
    ));

    public static final SideQualifier DEFAULT = NULL;

    public @Nullable EnumFacing getNonNullDirection(IBlockState blockState) {
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

    public ArrayList<@Nullable EnumFacing> resolve(IBlockState blockState) {
        ArrayList<@Nullable EnumFacing> rtn = new ArrayList<>(7);
        for (Side side : sides) {
            rtn.add(side.resolve(blockState));
        }
        return rtn;
    }

}
