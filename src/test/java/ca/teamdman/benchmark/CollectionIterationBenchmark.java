package ca.teamdman.benchmark;

import it.unimi.dsi.fastutil.objects.*;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("NotNullFieldNotInitialized")
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 5)
@Fork(value = 3, warmups = 1)
public class CollectionIterationBenchmark {
    private static final int N = 25;
    private List<String> arrayList;
    private HashSet<String> hashSet;
    private LinkedHashSet<String> linkedHashSet;
    private SpeedySet<String> speedySet;
    private ObjectOpenHashSet<String> objectOpenHashSet;
    private ObjectLinkedOpenHashSet<String> objectLinkedOpenHashSet;
    private ObjectArraySet<String> objectArraySet;
    private ObjectAVLTreeSet<String> objectAVLTreeSet;
    private ObjectRBTreeSet<String> objectRBTreeSet;
    private String[] array;

    @Setup(Level.Iteration)
    public void setup() {
        arrayList = new ArrayList<>();
        hashSet = new HashSet<>();
        linkedHashSet = new LinkedHashSet<>();
        array = new String[N];
        speedySet = new SpeedySet<>(String.class, N);
        objectOpenHashSet = new ObjectOpenHashSet<>();
        objectLinkedOpenHashSet = new ObjectLinkedOpenHashSet<>();
        objectArraySet = new ObjectArraySet<>();
        objectAVLTreeSet = new ObjectAVLTreeSet<>();
        objectRBTreeSet = new ObjectRBTreeSet<>();

        // Populate the collections with N strings
        for (int i = 0; i < N; i++) {
            String value = "Value" + i;
            arrayList.add(value);
            hashSet.add(value);
            linkedHashSet.add(value);
            array[i] = value;
            speedySet.add(value);
            objectOpenHashSet.add(value);
            objectLinkedOpenHashSet.add(value);
            objectArraySet.add(value);
            objectAVLTreeSet.add(value);
            objectRBTreeSet.add(value);
        }
    }

    @Benchmark
    public int iterateArrayList() {
        int count = 0;
        for (String s : arrayList) {
            count += s.length();
        }
        return count;
    }

    @Benchmark
    public int iterateHashSet() {
        int count = 0;
        for (String s : hashSet) {
            count += s.length();
        }
        return count;
    }

    @Benchmark
    public int iterateLinkedHashSet() {
        int count = 0;
        for (String s : linkedHashSet) {
            count += s.length();
        }
        return count;
    }

    @Benchmark
    public int iterateArray() {
        int count = 0;
        for (String s : array) {
            count += s.length();
        }
        return count;
    }

    @SuppressWarnings("ForLoopReplaceableByForEach")
    @Benchmark
    public int iterateArrayFor() {
        int count = 0;
        for (int i = 0; i < array.length; i++) {
            count += array[i].length();
        }
        return count;
    }

    @Benchmark
    public int iterateSpeedySet() {
        int count = 0;
        for (String s : speedySet.innerUnsafe()) {
            count += s.length();
        }
        return count;
    }

    @Benchmark
    public int iterateObjectOpenHashSet() {
        int count = 0;
        for (String s : objectOpenHashSet) {
            count += s.length();
        }
        return count;
    }

    @Benchmark
    public int iterateObjectLinkedOpenHashSet() {
        int count = 0;
        for (String s : objectLinkedOpenHashSet) {
            count += s.length();
        }
        return count;
    }

    @Benchmark
    public int iterateObjectArraySet() {
        int count = 0;
        for (String s : objectArraySet) {
            count += s.length();
        }
        return count;
    }

    @Benchmark
    public int iterateObjectAVLTreeSet() {
        int count = 0;
        for (String s : objectAVLTreeSet) {
            count += s.length();
        }
        return count;
    }

    @Benchmark
    public int iterateObjectRBTreeSet() {
        int count = 0;
        for (String s : objectRBTreeSet) {
            count += s.length();
        }
        return count;
    }

    public static void main(String[] args) throws RunnerException {
        Options options = new OptionsBuilder()
                .include(CollectionIterationBenchmark.class.getSimpleName()) // Run this benchmark class
                .forks(1) // Use one fork
                .build();
        new Runner(options).run();
    }
}
