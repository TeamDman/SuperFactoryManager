package ca.teamdman.sfm.common.compat.computercraft;

import ca.teamdman.sfm.common.util.BlockPosSet;
import dan200.computercraft.api.lua.LuaFunction;
import net.minecraft.core.BlockPos;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** An immutable, enumerable set of world positions exposed to Lua. */
public final class SFMBlockPosSetHandle {
    private final Supplier<BlockPosSet> loader;
    private BlockPosSet positions;
    private List<BlockPos> sortedPositions;

    SFMBlockPosSetHandle(Supplier<BlockPosSet> loader) {

        this.loader = loader;
    }

    private void ensureLoaded() {

        if (positions != null) {
            return;
        }
        positions = new BlockPosSet(loader.get());
        sortedPositions = positions
                .blockPosIterator()
                .stream()
                .map(BlockPos::immutable)
                .sorted(Comparator.comparingLong(BlockPos::asLong))
                .toList();
    }

    @LuaFunction(mainThread = true)
    public final int count() {

        ensureLoaded();
        return sortedPositions.size();
    }

    @LuaFunction(mainThread = true)
    public final Object[] position(int index) {

        ensureLoaded();
        if (index < 1 || index > sortedPositions.size()) {
            return new Object[]{null};
        }
        BlockPos position = sortedPositions.get(index - 1);
        return new Object[]{position.getX(), position.getY(), position.getZ()};
    }

    @LuaFunction(mainThread = true)
    public final boolean contains(
            int x,
            int y,
            int z
    ) {

        ensureLoaded();
        return positions.contains(new BlockPos(x, y, z));
    }

    @LuaFunction(mainThread = true)
    public final Map<Integer, Map<String, Integer>> toTable() {

        ensureLoaded();
        Map<Integer, Map<String, Integer>> result = new LinkedHashMap<>();
        for (int index = 0; index < sortedPositions.size(); index++) {
            BlockPos position = sortedPositions.get(index);
            result.put(index + 1, Map.of(
                    "x", position.getX(),
                    "y", position.getY(),
                    "z", position.getZ()
            ));
        }
        return result;
    }

    BlockPosSet positions() {

        ensureLoaded();
        return positions;
    }
}
