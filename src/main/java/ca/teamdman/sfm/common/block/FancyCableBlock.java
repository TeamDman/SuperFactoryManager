package ca.teamdman.sfm.common.block;

import ca.teamdman.sfm.common.cablenetwork.ICableBlock;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.function.Supplier;

public class FancyCableBlock extends CableBlock {
    public static final EnumProperty<CABLE_CONNECTION_TYPE> NORTH = EnumProperty.create("north",CABLE_CONNECTION_TYPE.class);
    public static final EnumProperty<CABLE_CONNECTION_TYPE> SOUTH = EnumProperty.create("south",CABLE_CONNECTION_TYPE.class);
    public static final EnumProperty<CABLE_CONNECTION_TYPE> EAST = EnumProperty.create("east",CABLE_CONNECTION_TYPE.class);
    public static final EnumProperty<CABLE_CONNECTION_TYPE> WEST = EnumProperty.create("west",CABLE_CONNECTION_TYPE.class);
    public static final EnumProperty<CABLE_CONNECTION_TYPE> UP = EnumProperty.create("up",CABLE_CONNECTION_TYPE.class);
    public static final EnumProperty<CABLE_CONNECTION_TYPE> DOWN = EnumProperty.create("down",CABLE_CONNECTION_TYPE.class);

    public static final VoxelShape SHAPE_CORE = Block.box(5, 5, 5, 11, 11, 11);
    public static final VoxelShape SHAPE_NORTH = Block.box(5, 5, 0, 11, 11, 5);
    public static final VoxelShape SHAPE_SOUTH = Block.box(5, 5, 11, 11, 11, 16);
    public static final VoxelShape SHAPE_EAST = Block.box(11, 5, 5, 16, 11, 11);
    public static final VoxelShape SHAPE_WEST = Block.box(0, 5, 5, 5, 11, 11);
    public static final VoxelShape SHAPE_UP = Block.box(5, 11, 5, 11, 16, 11);
    public static final VoxelShape SHAPE_DOWN = Block.box(5, 0, 5, 11, 5, 11);

    public static final Map<Direction, EnumProperty<CABLE_CONNECTION_TYPE>> DIRECTION_PROPERTIES = ImmutableMap.of(
            Direction.NORTH, NORTH,
            Direction.SOUTH, SOUTH,
            Direction.EAST, EAST,
            Direction.WEST, WEST,
            Direction.UP, UP,
            Direction.DOWN, DOWN
    );

    public FancyCableBlock() {
        super();
        registerDefaultState(
                defaultBlockState()
                        .setValue(NORTH, CABLE_CONNECTION_TYPE.AIR)
                        .setValue(SOUTH, CABLE_CONNECTION_TYPE.AIR)
                        .setValue(EAST, CABLE_CONNECTION_TYPE.AIR)
                        .setValue(WEST, CABLE_CONNECTION_TYPE.AIR)
                        .setValue(UP, CABLE_CONNECTION_TYPE.AIR)
                        .setValue(DOWN, CABLE_CONNECTION_TYPE.AIR)
        );
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return getState(defaultBlockState(), ctx.getLevel(), ctx.getClickedPos());
    }

    @SuppressWarnings("deprecation")
    @Override
    public void neighborChanged(
            BlockState state,
            Level level,
            BlockPos pos,
            Block block,
            BlockPos fromPos,
            boolean isMoving
    ) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);

        level.setBlockAndUpdate(pos, getState(level.getBlockState(pos), level, pos));
    }

    @SuppressWarnings("deprecation")
    @Override
    public VoxelShape getShape(
            BlockState state,
            BlockGetter world,
            BlockPos pos,
            CollisionContext ctx
    ) {
        return ShapeCache.getOrCompute(state, FancyCableBlock::getShape);
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(
            BlockState state,
            Direction dir,
            BlockState facingState,
            LevelAccessor world,
            BlockPos pos,
            BlockPos facingPos
    ) {
        return getState(state, world, pos);
    }

    protected static VoxelShape getShape(BlockState state) {
        var shape = SHAPE_CORE;

        shape = combineShapes(shape, SHAPE_NORTH, () -> state.getValue(NORTH) != CABLE_CONNECTION_TYPE.AIR);
        shape = combineShapes(shape, SHAPE_SOUTH, () -> state.getValue(SOUTH) != CABLE_CONNECTION_TYPE.AIR);
        shape = combineShapes(shape, SHAPE_EAST, () -> state.getValue(EAST) != CABLE_CONNECTION_TYPE.AIR);
        shape = combineShapes(shape, SHAPE_WEST, () -> state.getValue(WEST) != CABLE_CONNECTION_TYPE.AIR);
        shape = combineShapes(shape, SHAPE_UP, () -> state.getValue(UP) != CABLE_CONNECTION_TYPE.AIR);
        shape = combineShapes(shape, SHAPE_DOWN, () -> state.getValue(DOWN) != CABLE_CONNECTION_TYPE.AIR);

        return shape;
    }

    protected static VoxelShape combineShapes(
            VoxelShape shape1,
            VoxelShape shape2,
            Supplier<Boolean> condition
    ) {
        return condition.get() ? Shapes.or(shape1, shape2) : shape1;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(NORTH, SOUTH, EAST, WEST, UP, DOWN);
    }

    protected BlockState getState(
            BlockState currentState,
            LevelAccessor level,
            BlockPos pos
    ) {
        CABLE_CONNECTION_TYPE north = getConnection(level, pos, Direction.NORTH);
        CABLE_CONNECTION_TYPE south = getConnection(level, pos, Direction.SOUTH);
        CABLE_CONNECTION_TYPE east = getConnection(level, pos, Direction.EAST);
        CABLE_CONNECTION_TYPE west = getConnection(level, pos, Direction.WEST);
        CABLE_CONNECTION_TYPE up = getConnection(level, pos, Direction.UP);
        CABLE_CONNECTION_TYPE down = getConnection(level, pos, Direction.DOWN);

        return currentState
                .setValue(NORTH, north)
                .setValue(SOUTH, south)
                .setValue(EAST, east)
                .setValue(WEST, west)
                .setValue(UP, up)
                .setValue(DOWN, down);
    }

    protected boolean hasConnection(
            LevelAccessor level,
            BlockPos pos,
            Direction direction
    ) {
        // Directly connect to other cables
        BlockPos relative = pos.relative(direction);
        if (level.getBlockState(relative).getBlock() instanceof ICableBlock) {
            return true;
        }

        BlockEntity blockEntity = level.getBlockEntity(relative);
        if (blockEntity == null) {
            return false;
        }

        return blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, direction.getOpposite()).isPresent();
    }

    protected CABLE_CONNECTION_TYPE getConnection(
            LevelAccessor level,
            BlockPos pos,
            Direction direction
    ) {
        // Directly connect to other cables
        BlockPos relative = pos.relative(direction);
        if (level.getBlockState(relative).getBlock() instanceof CableBlock) {
            return CABLE_CONNECTION_TYPE.CABLE;
        }

        BlockEntity blockEntity = level.getBlockEntity(relative);
        if (blockEntity == null) {
            return CABLE_CONNECTION_TYPE.AIR;
        } else if (blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, direction.getOpposite()).isPresent()){
            return CABLE_CONNECTION_TYPE.INV;
        }
        return CABLE_CONNECTION_TYPE.AIR;
    }

    public enum CABLE_CONNECTION_TYPE implements StringRepresentable{
        AIR,
        CABLE,
        INV;

        @Override
        public String getSerializedName() {
            return this.name().toLowerCase();
        }
    }
}
