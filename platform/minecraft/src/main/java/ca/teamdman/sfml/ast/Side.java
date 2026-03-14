package ca.teamdman.sfml.ast;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public enum Side implements SfmlAstNode {
    TOP,
    BOTTOM,
    NORTH,
    SOUTH,
    EAST,
    WEST,
    LEFT,
    RIGHT,
    FRONT,
    BACK,
    NULL;


    public static Side fromDirection(@Nullable Direction direction) {

        if (direction == null) return NULL;
        return switch (direction) {
            case UP -> TOP;
            case DOWN -> BOTTOM;
            case NORTH -> NORTH;
            case SOUTH -> SOUTH;
            case EAST -> EAST;
            case WEST -> WEST;
        };
    }

    public @Nullable Direction resolve(BlockState blockState) {

        return switch (this) {
            case TOP -> Direction.UP;
            case BOTTOM -> Direction.DOWN;
            case NORTH -> Direction.NORTH;
            case SOUTH -> Direction.SOUTH;
            case EAST -> Direction.EAST;
            case WEST -> Direction.WEST;
            case LEFT -> blockState.getOptionalValue(BlockStateProperties.HORIZONTAL_FACING)
                    .or(() -> blockState.getOptionalValue(BlockStateProperties.FACING))
                    .map(Direction::getClockWise)
                    .orElse(null);
            case RIGHT -> blockState.getOptionalValue(BlockStateProperties.HORIZONTAL_FACING)
                    .or(() -> blockState.getOptionalValue(BlockStateProperties.FACING))
                    .map(Direction::getCounterClockWise)
                    .orElse(null);
            case FRONT -> blockState.getOptionalValue(BlockStateProperties.HORIZONTAL_FACING)
                    .or(() -> blockState.getOptionalValue(BlockStateProperties.FACING))
                    .orElse(null);
            case BACK -> blockState.getOptionalValue(BlockStateProperties.HORIZONTAL_FACING)
                    .or(() -> blockState.getOptionalValue(BlockStateProperties.FACING))
                    .map(Direction::getOpposite)
                    .orElse(null);
            case NULL -> null;
        };
    }

    @Override
    public List<? extends SfmlAstNode> getChildNodes() {

        return List.of();
    }
}
