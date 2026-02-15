package ca.teamdman.benchmark;

import it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectRBTreeMap;
import org.apache.logging.log4j.Level;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static org.openjdk.jmh.annotations.Level.Iteration;
import static org.openjdk.jmh.annotations.Level.Trial;

@SuppressWarnings("NotNullFieldNotInitialized")
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 5)
@Fork(value = 3, warmups = 1)
public class LogLevelCheckBenchmark {
    /// faster than logger.isEnabled
    private Map<Level, Predicate<Level>> defaultMap;
    private Map<Level, Predicate<Level>> fastMap1;
    private Map<Level, Predicate<Level>> fastMap2;
    private Map<Level, Predicate<Level>> fastMap3;
    private Random random;
    private Level[] levels;
    @SuppressWarnings("unused")
    private int count;

    @Setup(Trial)
    public void setupTrial() {
        defaultMap = Map.of(
                Level.OFF, currentLevel ->false,
                Level.FATAL, currentLevel -> currentLevel != Level.OFF,
                Level.ERROR, currentLevel -> currentLevel != Level.OFF && currentLevel != Level.FATAL,
                Level.WARN, currentLevel -> currentLevel != Level.OFF && currentLevel != Level.FATAL && currentLevel != Level.ERROR,
                Level.INFO, currentLevel -> currentLevel == Level.INFO || currentLevel == Level.DEBUG || currentLevel == Level.TRACE || currentLevel == Level.ALL,
                Level.DEBUG, currentLevel -> currentLevel == Level.DEBUG || currentLevel == Level.TRACE || currentLevel == Level.ALL,
                Level.TRACE, currentLevel -> currentLevel == Level.TRACE || currentLevel == Level.ALL,
                Level.ALL, currentLevel ->true
        );
        fastMap1 = new Object2ObjectOpenHashMap<>(defaultMap);
        fastMap2 = new Object2ObjectAVLTreeMap<>(defaultMap);
        fastMap3 = new Object2ObjectRBTreeMap<>(defaultMap);
        // We do not use Level.ALL or Level.OFF or Level.FATAL in SFM manager loggers
        levels = new Level[]{
                Level.ERROR,
                Level.WARN,
                Level.INFO,
                Level.DEBUG,
                Level.TRACE,
        };
    }

    @Setup(Iteration)
    public void setupIteration() {
        random = new Random();
        random.setSeed(123L);
        count = 0;
    }

    private boolean isLevelEnabledMap(Level attempting, Level currentLevel, Map<Level, Predicate<Level>> map) {
        return map.get(attempting).test(currentLevel);
    }

    private boolean isLevelEnabledIf(Level attempting, Level currentLevel) {
        if (attempting == Level.TRACE) {
            return currentLevel == Level.TRACE;
        }
        if (attempting == Level.DEBUG) {
            return currentLevel == Level.DEBUG || currentLevel == Level.TRACE;
        }
        if (attempting == Level.INFO) {
            return currentLevel == Level.INFO || currentLevel == Level.DEBUG || currentLevel == Level.TRACE;
        }
        if (attempting == Level.WARN) {
            return currentLevel == Level.WARN || currentLevel == Level.INFO || currentLevel == Level.DEBUG || currentLevel == Level.TRACE;
        }
        if (attempting == Level.ERROR) {
            return currentLevel == Level.ERROR || currentLevel == Level.WARN || currentLevel == Level.INFO || currentLevel == Level.DEBUG || currentLevel == Level.TRACE;
        }
        return true;
    }

    private boolean isLevelEnabledInt(Level attempting, Level currentLevel) {
        return currentLevel.isLessSpecificThan(attempting);
    }

    public int work(String bruh) {
        return bruh.length();
    }

    @Benchmark
    public void measureDefault() {
        Level currentLogLevel = levels[random.nextInt(levels.length)];
        Level attempting = levels[random.nextInt(levels.length)];
        if (isLevelEnabledMap(attempting, currentLogLevel, defaultMap)) {
            count += work("bruh");
        }
    }

    @Benchmark
    public void measureFast1() {
        Level currentLogLevel = levels[random.nextInt(levels.length)];
        Level attempting = levels[random.nextInt(levels.length)];
        if (isLevelEnabledMap(attempting, currentLogLevel, fastMap1)) {
            count += work("bruh");
        }
    }

    @Benchmark
    public void measureFast2() {
        Level currentLogLevel = levels[random.nextInt(levels.length)];
        Level attempting = levels[random.nextInt(levels.length)];
        if (isLevelEnabledMap(attempting, currentLogLevel, fastMap2)) {
            count += work("bruh");
        }
    }

    @Benchmark
    public void measureFast3() {
        Level currentLogLevel = levels[random.nextInt(levels.length)];
        Level attempting = levels[random.nextInt(levels.length)];
        if (isLevelEnabledMap(attempting, currentLogLevel, fastMap3)) {
            count += work("bruh");
        }
    }

    @Benchmark
    public void measureIf() {
        Level currentLogLevel = levels[random.nextInt(levels.length)];
        Level attempting = levels[random.nextInt(levels.length)];
        if (isLevelEnabledIf(attempting, currentLogLevel)) {
            count += work("bruh");
        }
    }
    @Benchmark
    public void measureInt() {
        Level currentLogLevel = levels[random.nextInt(levels.length)];
        Level attempting = levels[random.nextInt(levels.length)];
        if (isLevelEnabledInt(attempting, currentLogLevel)) {
            count += work("bruh");
        }
    }

    public static void main(String[] args) throws RunnerException {
        Options options = new OptionsBuilder()
                .include(LogLevelCheckBenchmark.class.getSimpleName())
                .forks(1)
                .shouldDoGC(false)
                .build();
        new Runner(options).run();
    }
}
