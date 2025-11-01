package ca.teamdman.sfm.common.util;

import net.minecraft.util.math.BlockPos;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class SFMStreamUtils {
    /**
     * Gets a stream using a self-feeding mapping function. Prevents the
     * re-traversal of elements that have been visited before.
     *
     * @param operator Consumes queue elements to build the result set and
     *                 append the next queue elements
     * @param first    Initial value, not checked against the filter
     * @param <T>      Type that the mapper consumes and produces
     * @return Stream result after termination of the recursive mapping process
     */
    public static <T, R> Stream<R> getRecursiveStream(
            RecursiveBuilder<T, R> operator,
            T first
    ) {
        Set<T> visitDebounce = new HashSet<>();
        Deque<T> toVisit = new ArrayDeque<>();
        toVisit.add(first);
        visitDebounce.add(first);
        return getRecursiveStream(operator, visitDebounce, toVisit);
    }

    public static <T, R> Stream<R> getRecursiveStream(
            RecursiveBuilder<T, R> operator,
            Set<T> visitDebounce,
            Deque<T> toVisit
    ) {
        Stream.Builder<R> builder = Stream.builder();
        while (!toVisit.isEmpty()) {
            T current = toVisit.pop();
            operator.accept(
                    current,
                    next -> {
                        if (!visitDebounce.contains(next)) {
                            visitDebounce.add(next);
                            toVisit.add(next);
                        }
                    },
                    builder::add
            );
        }
        return builder.build();
    }

    public static Stream<BlockPos> get3DNeighboursIncludingKittyCorner(@NotStored BlockPos pos) {
        Stream.Builder<BlockPos> builder = Stream.builder();
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && y == 0 && z == 0) continue;
                    builder.accept(pos.offset(x, y, z).immutable());
                }
            }
        }
        return builder.build();
    }

    public static Stream<BlockPos> get3DNeighbours(@Stored BlockPos pos) {
        return Arrays.stream(SFMDirections.DIRECTIONS_WITHOUT_NULL).map(d -> pos.offset(d.getNormal()));
    }

    public interface RecursiveBuilder<T, R> {
        void accept(
                T current,
                Consumer<T> next,
                Consumer<R> results
        );
    }
}
