package ca.teamdman.sfml.ast;

import net.minecraft.block.BlockDirectional;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import org.jetbrains.annotations.Nullable;

public enum Side implements ASTNode {
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


    public static Side fromDirection(@Nullable EnumFacing direction) {

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

    public @Nullable EnumFacing resolve(IBlockState blockState) {
        try {
            return switch (this) {
                case TOP -> EnumFacing.UP;
                case BOTTOM -> EnumFacing.DOWN;
                case NORTH -> EnumFacing.NORTH;
                case SOUTH -> EnumFacing.SOUTH;
                case EAST -> EnumFacing.EAST;
                case WEST -> EnumFacing.WEST;
            case LEFT ->
                        blockState.getProperties().containsKey(BlockDirectional.FACING) ? getClockWise(blockState.getValue(
                            BlockDirectional.FACING)) : null;
            case RIGHT ->
                        blockState.getProperties().containsKey(BlockDirectional.FACING) ? getCounterClockWise(blockState.getValue(
                            BlockDirectional.FACING)) : null;
                case FRONT -> blockState.getProperties().containsKey(BlockDirectional.FACING) ? blockState.getValue(
                        BlockDirectional.FACING) : null;
                case BACK -> blockState.getProperties().containsKey(BlockDirectional.FACING) ? blockState.getValue(
                        BlockDirectional.FACING).getOpposite() : null;
                case NULL -> null;
            };
        } catch (Exception e) {
            // Fix #445 where UP and DOWN directions cannot be rotated to determine relative left/right faces.
            return null;
        }
    }

    @Nullable
    public EnumFacing getClockWise(EnumFacing $facing) {
        return switch ($facing) {
            case NORTH -> EnumFacing.EAST;
            case SOUTH -> EnumFacing.WEST;
            case WEST -> EnumFacing.NORTH;
            case EAST -> EnumFacing.SOUTH;
            default -> null;
        };
    }

    @Nullable
    public EnumFacing getCounterClockWise(EnumFacing $facing) {
        return switch ($facing) {
            case NORTH -> EnumFacing.WEST;
            case SOUTH -> EnumFacing.EAST;
            case WEST -> EnumFacing.SOUTH;
            case EAST -> EnumFacing.NORTH;
            default -> null;
        };
    }
}
