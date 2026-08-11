package ca.teamdman.sfm.test;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.TestInstance;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ObjectPoolPerformanceComparisonTests {

    private static Thingy[] pool = new Thingy[1];
    private static int index = -1;

    private static Thingy acquire(int x, int y, int slot) {
        if (index == -1) {
            return new Thingy(x, y, slot);
        } else {
            Thingy obj = pool[index];
            index--;
            obj.init(x, y, slot);
            return obj;
        }
    }

    private static void release(List<Thingy> slots) {
        // handle resizing
        if (index + slots.size() >= pool.length) {
            int slotsFree = pool.length - index - 1;
            int newLength = pool.length + slots.size() - slotsFree;
            pool = Arrays.copyOf(pool, newLength);
        }
        // add to pool
        for (Thingy slot : slots) {
            index++;
            pool[index] = slot;
        }
    }

    @RepeatedTest(10)
    public void noObjectPool() {
        for (int x = 0; x < 25; x++) {
            for (int y = 0; y < 25; y++) {
                for (int slot = 0; slot < 2700; slot++) {
                    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
                    List<Thingy> things = new ArrayList<>();
                    things.add(new Thingy(x, y, slot));
                }
                // do some junk work to complicate stuff
                for (int i = 0; i < 100; i++) {
                    var pattern = Pattern.compile("[a-zA-Z]{4,6}jkl[^_]");
                    //noinspection ResultOfMethodCallIgnored
                    pattern.matcher("abcdefghijklmnopqrstuvwxyz").matches();
                }
            }
        }
    }

    @RepeatedTest(10)
    public void withObjectPool() {
        for (int x = 0; x < 25; x++) {
            for (int y = 0; y < 25; y++) {
                List<Thingy> things = new ArrayList<>();
                for (int slot = 0; slot < 2700; slot++) {
                    things.add(acquire(x, y, slot));
                }
                release(things);
                // do some junk work to complicate stuff
                for (int i = 0; i < 100; i++) {
                    var pattern = Pattern.compile("[a-zA-Z]{4,6}jkl[^_]");
                    //noinspection ResultOfMethodCallIgnored
                    pattern.matcher("abcdefghijklmnopqrstuvwxyz").matches();
                }
            }
        }
    }

    @SuppressWarnings("unused")
    private static class Thingy {
        @SuppressWarnings("unused")
        public int x;
        @SuppressWarnings("unused")
        public int y;
        @SuppressWarnings("unused")
        public int slot;

        public Thingy(int x, int y, int slot) {
            this.init(x, y, slot);
        }

        public void init(int x, int y, int slot) {
            this.x = x;
            this.y = y;
            this.slot = slot;
        }
    }
}
