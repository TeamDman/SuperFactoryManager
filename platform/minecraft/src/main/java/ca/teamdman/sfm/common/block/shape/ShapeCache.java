package ca.teamdman.sfm.common.block.shape;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public final class ShapeCache {
    private static final Map<BlockState, VoxelShape> STORAGE = new HashMap<>();

    private ShapeCache() {
    }

    public static VoxelShape getOrCompute(BlockState state, Function<BlockState, VoxelShape> computeFunction) {
        return STORAGE.computeIfAbsent(state, computeFunction);
    }
}
