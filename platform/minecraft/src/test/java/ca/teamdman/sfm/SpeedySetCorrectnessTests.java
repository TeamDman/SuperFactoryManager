package ca.teamdman.sfm;

import ca.teamdman.benchmark.SpeedySet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SpeedySetCorrectnessTests {

    @Test
    public void testAddElements() {
        SpeedySet<String> speedySet = new SpeedySet<>(String.class, 2);
        assertTrue(speedySet.add("Apple"));
        assertTrue(speedySet.add("Banana"));
        assertTrue(speedySet.add("Cherry"));

        String[] expected = {"Apple", "Banana", "Cherry"};
        assertArrayEquals(expected, speedySet.innerUnsafe());
    }

    @Test
    public void testAddDuplicateElements() {
        SpeedySet<String> speedySet = new SpeedySet<>(String.class, 2);
        assertTrue(speedySet.add("Apple"));
        assertFalse(speedySet.add("Apple")); // Duplicate
        assertTrue(speedySet.add("Banana"));
        assertFalse(speedySet.add("Banana")); // Duplicate

        String[] expected = {"Apple", "Banana"};
        assertArrayEquals(expected, speedySet.innerUnsafe());
    }

    @Test
    public void testInsertionOrder() {
        SpeedySet<Integer> speedySet = new SpeedySet<>(Integer.class, 3);
        speedySet.add(3);
        speedySet.add(1);
        speedySet.add(2);

        Integer[] expected = {3, 1, 2};
        assertArrayEquals(expected, speedySet.innerUnsafe());
    }

    @Test
    public void testResizeArray() {
        SpeedySet<Integer> speedySet = new SpeedySet<>(Integer.class, 2);
        speedySet.add(1);
        speedySet.add(2);
        speedySet.add(3); // Triggers resizing
        speedySet.add(4);

        Integer[] expected = {1, 2, 3, 4};
        assertArrayEquals(expected, speedySet.innerUnsafe());
    }

    @Test
    public void testAddNullElements() {
        SpeedySet<String> speedySet = new SpeedySet<>(String.class, 2);
        assertTrue(speedySet.add(null));
        assertFalse(speedySet.add(null)); // Duplicate null
        assertTrue(speedySet.add("Apple"));

        String[] expected = {null, "Apple"};
        assertArrayEquals(expected, speedySet.innerUnsafe());
    }

    @Test
    public void testInitialCapacityZero() {
        SpeedySet<String> speedySet = new SpeedySet<>(String.class, 0);
        assertTrue(speedySet.add("Apple"));
        assertTrue(speedySet.add("Banana"));

        String[] expected = {"Apple", "Banana"};
        assertArrayEquals(expected, speedySet.innerUnsafe());
    }

    @Test
    public void testAddMultipleElements() {
        SpeedySet<Integer> speedySet = new SpeedySet<>(Integer.class, 5);
        for (int i = 0; i < 10; i++) {
            speedySet.add(i);
        }

        Integer[] expected = new Integer[10];
        for (int i = 0; i < 10; i++) {
            expected[i] = i;
        }
        assertArrayEquals(expected, speedySet.innerUnsafe());
    }

    @Test
    public void testNoSideEffectsOnFailedAdd() {
        SpeedySet<String> speedySet = new SpeedySet<>(String.class, 2);
        speedySet.add("Apple");
        speedySet.add("Banana");
        int sizeBefore = speedySet.innerUnsafe().length;
        assertFalse(speedySet.add("Apple")); // Duplicate
        int sizeAfter = speedySet.innerUnsafe().length;

        assertEquals(sizeBefore, sizeAfter);
    }

//    @Test
//    public void testInnerMethodImmutability() {
//        SpeedySet<String> speedySet = new SpeedySet<>(String.class, 2);
//        speedySet.add("Apple");
//        String[] innerArray = speedySet.innerUnsafe();
//        innerArray[0] = "Modified";
//
//        String[] expected = {"Apple"};
//        assertArrayEquals(expected, speedySet.innerUnsafe());
//    }
}
