package ca.teamdman.benchmark;

import ca.teamdman.sfm.common.util.SFMResourceLocation;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectRBTreeMap;
import net.minecraft.resources.ResourceLocation;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.Random;
import java.util.concurrent.TimeUnit;

import static org.openjdk.jmh.annotations.Level.Iteration;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 5)
@Fork(value = 3, warmups = 1)
public class ResourceTypeCacheBenchmark {
    private Int2ObjectArrayMap<String> intCache = new Int2ObjectArrayMap<>();
    private Object2ObjectArrayMap<ResourceLocation, String> objArrayCache = new Object2ObjectArrayMap<>();
    private Object2ObjectOpenHashMap<ResourceLocation, String> objOpenCache = new Object2ObjectOpenHashMap<>();
    private Object2ObjectAVLTreeMap<ResourceLocation, String> objAVLCache = new Object2ObjectAVLTreeMap<>();
    private Object2ObjectRBTreeMap<ResourceLocation, String> objRBTreeCache = new Object2ObjectRBTreeMap<>();
    private ResourceLocation[] values;
    private Random random;

    @Setup()
    public void setup() {
        values = new ResourceLocation[]{
                SFMResourceLocation.fromSFMPath("item"),
                SFMResourceLocation.fromSFMPath("fluid"),
                SFMResourceLocation.fromSFMPath("gas"),
                SFMResourceLocation.fromSFMPath("forge_energy"),
                SFMResourceLocation.fromSFMPath("infusion"),
                SFMResourceLocation.fromSFMPath("mana"),
                SFMResourceLocation.fromSFMPath("bruh"),
                };
        for (ResourceLocation value : values) {
            intCache.put(value.hashCode(), value.toString());
            objArrayCache.put(value, value.toString());
            objOpenCache.put(value, value.toString());
            objAVLCache.put(value, value.toString());
            objRBTreeCache.put(value, value.toString());
        }
    }

    @Setup(Iteration)
    public void setupIteration() {
        random = new Random();
        random.setSeed(123L);
    }

    @Benchmark
    public void accessIntCache() {
        intCache.get(values[random.nextInt(values.length)].hashCode());
    }

    @Benchmark
    public void accessObjArrayCache() {
        objArrayCache.get(values[random.nextInt(values.length)]);
    }

    @Benchmark
    public void accessObjOpenCache() {
        objOpenCache.get(values[random.nextInt(values.length)]);
    }

    @Benchmark
    public void accessObjAVLCache() {
        objAVLCache.get(values[random.nextInt(values.length)]);
    }

    @Benchmark
    public void accessObjRBTreeCache() {
        objRBTreeCache.get(values[random.nextInt(values.length)]);
    }

    public static void main(String[] args) throws RunnerException {
        Options options = new OptionsBuilder()
                .include(ResourceTypeCacheBenchmark.class.getSimpleName())
                .forks(1)
                .shouldDoGC(false)
                .build();
        new Runner(options).run();
    }
}
