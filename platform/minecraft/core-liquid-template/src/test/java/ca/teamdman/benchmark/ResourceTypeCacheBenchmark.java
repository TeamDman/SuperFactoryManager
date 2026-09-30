package ca.teamdman.benchmark;

import ca.teamdman.sfm.common.util.SFMResourceLocation;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectRBTreeMap;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import net.minecraft.resources.ResourceLocation;
{% when '26.1.2' %}
import net.minecraft.resources.Identifier;
{% endcase %}
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
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    private Object2ObjectArrayMap<ResourceLocation, String> objArrayCache = new Object2ObjectArrayMap<>();
    private Object2ObjectOpenHashMap<ResourceLocation, String> objOpenCache = new Object2ObjectOpenHashMap<>();
    private Object2ObjectAVLTreeMap<ResourceLocation, String> objAVLCache = new Object2ObjectAVLTreeMap<>();
    private Object2ObjectRBTreeMap<ResourceLocation, String> objRBTreeCache = new Object2ObjectRBTreeMap<>();
    private ResourceLocation[] values;
{% when '26.1.2' %}
    private Object2ObjectArrayMap<Identifier, String> objArrayCache = new Object2ObjectArrayMap<>();
    private Object2ObjectOpenHashMap<Identifier, String> objOpenCache = new Object2ObjectOpenHashMap<>();
    private Object2ObjectAVLTreeMap<Identifier, String> objAVLCache = new Object2ObjectAVLTreeMap<>();
    private Object2ObjectRBTreeMap<Identifier, String> objRBTreeCache = new Object2ObjectRBTreeMap<>();
    private Identifier[] values;
{% endcase %}
    private Random random;

    @Setup()
    public void setup() {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        values = new ResourceLocation[]{
{% when '26.1.2' %}
        values = new Identifier[]{
{% endcase %}
                SFMResourceLocation.fromSFMPath("item"),
                SFMResourceLocation.fromSFMPath("fluid"),
                SFMResourceLocation.fromSFMPath("gas"),
                SFMResourceLocation.fromSFMPath("forge_energy"),
                SFMResourceLocation.fromSFMPath("infusion"),
                SFMResourceLocation.fromSFMPath("mana"),
                SFMResourceLocation.fromSFMPath("bruh"),
                };
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        for (ResourceLocation value : values) {
{% when '26.1.2' %}
        for (Identifier value : values) {
{% endcase %}
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
