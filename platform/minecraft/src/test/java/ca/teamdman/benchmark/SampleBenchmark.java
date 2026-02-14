package ca.teamdman.benchmark;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

@SuppressWarnings("NotNullFieldNotInitialized")
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 5)
@Fork(value = 3, warmups = 1)
public class SampleBenchmark {
    private int[] array;

    @Setup(Level.Iteration)
    public void setup() {
        array = new int[1000];
        for (int i = 0; i < array.length; i++) {
            array[i] = i;
        }
    }

    @Benchmark
    public int sumArray() {
        int sum = 0;
        for (int i : array) {
            sum += i;
        }
        return sum;
    }

    public static void main(String[] args) throws RunnerException {
//        org.openjdk.jmh.Main.main(args);
        Options options = new OptionsBuilder()
                .include(SampleBenchmark.class.getSimpleName())
//                .include(".*")
                .forks(1)
                .shouldDoGC(false)
                .build();
        new Runner(options).run();
    }
}

