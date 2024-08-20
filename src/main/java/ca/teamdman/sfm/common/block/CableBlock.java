package ca.teamdman.sfm.common.block;

import ca.teamdman.sfm.common.block.shape.ShapeCache;
import ca.teamdman.sfm.common.cablenetwork.CableNetworkManager;
import ca.teamdman.sfm.common.cablenetwork.ICableBlock;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.function.Supplier;

public class CableBlock extends Block implements ICableBlock {

    public static final BooleanProperty NORTH = BooleanProperty.create("north");
    public static final BooleanProperty SOUTH = BooleanProperty.create("south");
    public static final BooleanProperty EAST = BooleanProperty.create("east");
    public static final BooleanProperty WEST = BooleanProperty.create("west");
    public static final BooleanProperty UP = BooleanProperty.create("up");
    public static final BooleanProperty DOWN = BooleanProperty.create("down");

    public static final VoxelShape SHAPE_CORE = Block.box(4, 4, 4, 12, 12, 12);
    public static final VoxelShape SHAPE_NORTH = Block.box(5, 5, 0, 11, 11, 5);
    public static final VoxelShape SHAPE_SOUTH = Block.box(5, 5, 11, 11, 11, 16);
    public static final VoxelShape SHAPE_EAST = Block.box(11, 5, 5, 16, 11, 11);
    public static final VoxelShape SHAPE_WEST = Block.box(0, 5, 5, 5, 11, 11);
    public static final VoxelShape SHAPE_UP = Block.box(5, 11, 5, 11, 16, 11);
    public static final VoxelShape SHAPE_DOWN = Block.box(5, 0, 5, 11, 5, 11);

    public static final Map<Direction, BooleanProperty> DIRECTION_PROPERTIES = ImmutableMap.of(
            Direction.NORTH, NORTH,
            Direction.SOUTH, SOUTH,
            Direction.EAST, EAST,
            Direction.WEST, WEST,
            Direction.UP, UP,
            Direction.DOWN, DOWN
    );

    public CableBlock() {
        super(Block.Properties
                .of(Material.METAL)
                .destroyTime(1f)
                .sound(SoundType.METAL));

        registerDefaultState(
                defaultBlockState()
                        .setValue(NORTH, false)
                        .setValue(SOUTH, false)
                        .setValue(EAST, false)
                        .setValue(WEST, false)
                        .setValue(UP, false)
                        .setValue(DOWN, false)
        );
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);

        builder.add(NORTH, SOUTH, EAST, WEST, UP, DOWN);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return getState(defaultBlockState(), ctx.getLevel(), ctx.getClickedPos());
    }

    @SuppressWarnings("deprecation")
    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);

        level.setBlockAndUpdate(pos, getState(level.getBlockState(pos), level, pos));
    }

    @SuppressWarnings("deprecation")
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext ctx) {
        return ShapeCache.getOrCompute(state, CableBlock::getShape);
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(BlockState state, Direction dir, BlockState facingState, LevelAccessor world, BlockPos pos, BlockPos facingPos) {
        return getState(state, world, pos);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        CableNetworkManager.onCablePlaced(level, pos);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        CableNetworkManager.onCableRemoved(level, pos);
    }

    protected BlockState getState(BlockState currentState, LevelAccessor level, BlockPos pos) {
        boolean north = hasConnection(level, pos, Direction.NORTH);
        boolean south = hasConnection(level, pos, Direction.SOUTH);
        boolean east = hasConnection(level, pos, Direction.EAST);
        boolean west = hasConnection(level, pos, Direction.WEST);
        boolean up = hasConnection(level, pos, Direction.UP);
        boolean down = hasConnection(level, pos, Direction.DOWN);

        return currentState
                .setValue(NORTH, north)
                .setValue(SOUTH, south)
                .setValue(EAST, east)
                .setValue(WEST, west)
                .setValue(UP, up)
                .setValue(DOWN, down);
    }

    protected boolean hasConnection(LevelAccessor level, BlockPos pos, Direction direction) {
        // Directly connect to other cables
        // TODO: Perhaps we should make all SFM blocks implement something like 'INetworkNode' and just check that?
        if (level.getBlockState(pos.relative(direction)).getBlock() instanceof CableBlock) {
            return true;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos.relative(direction));
        if (blockEntity == null) {
            return false;
        }

        // TODO: Handle other capabilities
        return blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent();
    }

    protected static VoxelShape getShape(BlockState state) {
        var shape = SHAPE_CORE;

        shape = combineShapes(shape, SHAPE_NORTH, () -> state.getValue(NORTH));
        shape = combineShapes(shape, SHAPE_SOUTH, () -> state.getValue(SOUTH));
        shape = combineShapes(shape, SHAPE_EAST, () -> state.getValue(EAST));
        shape = combineShapes(shape, SHAPE_WEST, () -> state.getValue(WEST));
        shape = combineShapes(shape, SHAPE_UP, () -> state.getValue(UP));
        shape = combineShapes(shape, SHAPE_DOWN, () -> state.getValue(DOWN));

        return shape;
    }

    protected static VoxelShape combineShapes(VoxelShape shape1, VoxelShape shape2, Supplier<Boolean> condition) {
        return condition.get() ? Shapes.or(shape1, shape2) : shape1;
    }
}
