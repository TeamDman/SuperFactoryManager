package ca.teamdman.sfml.ast;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public record SideQualifier(List<Side> sides) implements SfmlAstNode {
    public static final SideQualifier NULL = new SideQualifier(List.of(Side.NULL));

    public static final SideQualifier ALL = new SideQualifier(List.of(
            Side.TOP, Side.BOTTOM, Side.NORTH, Side.SOUTH, Side.EAST, Side.WEST, Side.NULL
    ));

    public ArrayList<@Nullable Direction> resolve(BlockState blockState) {
        ArrayList<@Nullable Direction> rtn = new ArrayList<>(7);
        for (Side side : sides) {
            rtn.add(side.resolve(blockState));
        }
        return rtn;
    }

    @Override
    public List<Side> getChildNodes() {

        return sides;
    }

}
