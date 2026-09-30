package ca.teamdman.sfm.common.util;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
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

    public static <T, R> Stream<R> getRecursiveStream(
            RecursiveBuilder<T, R> operator,
            Deque<T> toVisit
    ) {
        Set<T> visitDebounce = new HashSet<>();
        return getRecursiveStream(operator, visitDebounce, toVisit);
    }

    public interface RecursiveBuilder<T, R> {
        void accept(
                T current,
                Consumer<T> next,
                Consumer<R> results
        );
    }

    /// Helper function since you get "method reference not expected here"
    /// if you don't manually cast in an enhanced for-loop
    /// ```java
    /// Stream<Block> blocks = something;
    /// for (Block block : blocks::iterator) // errors
    /// for (Block block : (Iterable<Block>) blocks::iterator) // ugly
    /// for (Block block : SFMStreamUtils.iterate(blocks)) // not as manual
    /// ```
    public static <T> Iterable<T> iterate(Stream<T> stream) {
        return stream::iterator;
    }
}
