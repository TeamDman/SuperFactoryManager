// SPDX-License-Identifier: MPL-2.0
import ca.teamdman.sfm.client.search.nucleo.NucleoMatcher;
import ca.teamdman.sfm.client.search.nucleo.NucleoSearch;
import java.nio.file.*;
import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

/** Single-process descriptive benchmark, not JMH or a Minecraft frame-time test. */
public class Benchmark {
    static volatile long sink;
    static long score(NucleoMatcher matcher, List<NucleoSearch.Entry<Integer>> entries) {
        long checksum = 0;
        for (var entry : entries) checksum += matcher.score(entry.candidate());
        return checksum;
    }
    static void measure(int n, String query, String mode, LongSupplier run) {
        long start = System.nanoTime();
        sink = run.getAsLong();
        double cold = (System.nanoTime() - start) / 1e6;
        for (int i = 0; i < 6; i++) sink = run.getAsLong();
        long[] samples = new long[21];
        var bean = ManagementFactory.getThreadMXBean();
        var allocation = bean instanceof com.sun.management.ThreadMXBean b ? b : null;
        long id = Thread.currentThread().getId();
        long before = allocation == null ? -1 : allocation.getThreadAllocatedBytes(id);
        for (int i = 0; i < samples.length; i++) {
            start = System.nanoTime();
            sink = run.getAsLong();
            samples[i] = System.nanoTime() - start;
        }
        long bytes = allocation == null ? -1 : (allocation.getThreadAllocatedBytes(id) - before) / samples.length;
        Arrays.sort(samples);
        System.out.printf(Locale.ROOT, "%d,%s,%s,%.3f,%.3f,%.3f,%d,%d%n", n, query, mode,
                cold, samples[10] / 1e6, samples[19] / 1e6, bytes, sink);
    }
    public static void main(String[] args) throws Exception {
        List<String> data = Files.readAllLines(Path.of(args[0]));
        System.out.println("count,query,mode,first_ms,p50_ms,p95_ms,allocated_bytes,checksum");
        for (int n : new int[]{1000, 10000, 50000, 100000}) {
            long start = System.nanoTime();
            var entries = new ArrayList<NucleoSearch.Entry<Integer>>(n);
            for (int i = 0; i < n; i++) entries.add(new NucleoSearch.Entry<>(i, data.get(i)));
            var search = new NucleoSearch<>(entries);
            System.err.printf(Locale.ROOT, "prepare %d entries: %.3f ms%n", n, (System.nanoTime() - start) / 1e6);
            for (String query : List.of("", "i", "iron", "ii", "m:ii", "cog", "copper_plate", "zzzz_missing")) {
                var matcher = new NucleoMatcher(query);
                measure(n, query, "java-score", () -> score(matcher, entries));
                measure(n, query, "java-top50-highlight", () -> search.search(query, NucleoMatcher.Options.DEFAULT,
                        50, () -> false).stream().mapToLong(r -> r.inputIndex() + r.match().score()).sum());
            }
            AtomicInteger checks = new AtomicInteger();
            start = System.nanoTime();
            try { search.search("i", NucleoMatcher.Options.DEFAULT, 50, () -> checks.incrementAndGet() > 100); }
            catch (CancellationException expected) {
                System.err.printf(Locale.ROOT, "cancel after 100 checks at %d entries: %.3f ms%n", n,
                        (System.nanoTime() - start) / 1e6);
            }
        }
    }
}
