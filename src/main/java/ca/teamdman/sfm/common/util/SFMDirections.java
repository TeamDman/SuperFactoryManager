package ca.teamdman.sfm.common.util;

import com.github.bsideup.jabel.Desugar;
import net.minecraft.util.EnumFacing;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;

import static net.minecraft.util.EnumFacing.*;

public class SFMDirections {
    /// Optimization to avoid creating a new array every time
    public static final EnumFacing[] DIRECTIONS_WITHOUT_NULL = values();
    /// Optimization to avoid creating a new array every time. Null is position 0
    public static final EnumFacing[] DIRECTIONS_WITH_NULL = new EnumFacing[]{
            null,
            NORTH,
            SOUTH,
            EAST,
            WEST,
            UP,
            DOWN
    };

    @Desugar
    public record NullableDirectionEnumMap<T>(T[] buckets) {
        public NullableDirectionEnumMap() {
            //noinspection unchecked
            this((T[]) new Object[DIRECTIONS_WITH_NULL.length]);
        }

        @SuppressWarnings("unused")
        public boolean containsKey(@Nullable EnumFacing direction) {
            return buckets[keyFor(direction)] != null;
        }

        public void forEach(BiConsumer<EnumFacing, T> callback) {
            for (EnumFacing direction : DIRECTIONS_WITH_NULL) {
                T value = buckets[keyFor(direction)];
                if (value != null) {
                    callback.accept(direction, value);
                }
            }
        }

        public void remove(@Nullable EnumFacing direction) {
            buckets[keyFor(direction)] = null;
        }

        public boolean isEmpty() {
            for (T bucket : buckets) {
                if (bucket != null) {
                    return false;
                }
            }
            return true;
        }

        public void put(
                @Nullable EnumFacing direction,
                T value
        ) {
            buckets[keyFor(direction)] = value;
        }

        public @Nullable T get(@Nullable EnumFacing direction) {
            return buckets[keyFor(direction)];
        }

        public int size() {
            int count = 0;
            for (T bucket : buckets) {
                if (bucket != null) {
                    count++;
                }
            }
            return count;
        }

        private int keyFor(@Nullable EnumFacing direction) {
            return direction == null ? 0 : direction.ordinal() + 1;
        }
    }
}
