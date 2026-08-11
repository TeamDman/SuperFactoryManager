package ca.teamdman.benchmark;

import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Set;

/// Ended up not using this because a primitive array was easier lol
/// This was intended to allow iteration but avoiding the object allocations that LinkedHashSet and HashSet perform
public class SpeedySet<T> {
    private final Set<T> set;
    private final Class<T> clazz;
    private T[] array;
    private int size = 0;

    @SuppressWarnings("unchecked")
    public SpeedySet(
            Class<T> clazz,
            int initialCapacity
    ) {
        this.clazz = clazz;
        this.array = (T[]) Array.newInstance(clazz, initialCapacity);
        this.set = new ObjectLinkedOpenHashSet<>();
    }

    @SuppressWarnings("unchecked")
    public boolean add(@Nullable T item) {
        if (set.contains(item)) {
            return false;
        }
        if (size == array.length) {
            int newCapacity = (array.length == 0) ? 1 : array.length * 2;
            T[] newArray = (T[]) Array.newInstance(clazz, newCapacity);
            System.arraycopy(array, 0, newArray, 0, array.length);
            array = newArray;
        }
        array[size++] = item;
        set.add(item);
        return true;
    }

    public T[] innerUnsafe() {
        return Arrays.copyOf(array, size);
    }
}
