import ca.teamdman.sfm.client.search.*;
import ca.teamdman.sfm.client.search.nucleo.NucleoMatcher;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Actual reviewed matcher binding plus deterministic task publication; no Minecraft/UI claim. */
public class NucleoIntegrationProbe {
    static int checks;
    static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); checks++; }
    static SFMExplorerNucleoAdapter.RowMatch match(String query, boolean matchCase, String candidate) {
        return SFMExplorerNucleoAdapter.compile(query, matchCase).match(candidate, work -> {});
    }
    record Context(String query, boolean matchCase, String root, String sort, int expanded, long generation) { }
    public static void main(String[] args) throws Exception {
        check(match("😀x", false, "a😀_x").utf16Spans().equals(List.of(
                new SFMExplorerNucleoAdapter.Span(1, 3), new SFMExplorerNucleoAdapter.Span(4, 5))), "original supplementary spans");
        check(match("e", false, "é").utf16Spans().equals(List.of(new SFMExplorerNucleoAdapter.Span(0, 1))), "normalized original accent span");
        check(match("ab", false, "a\u0301b").utf16Spans().equals(List.of(
                new SFMExplorerNucleoAdapter.Span(0, 1), new SFMExplorerNucleoAdapter.Span(2, 3))), "combining positions");
        check(match("I", false, "iron").matches() && !match("I", true, "iron").matches(), "RESPECT versus IGNORE, not SMART case");
        check(!match("é", false, "e").matches(), "SMART normalization asymmetry");
        check(!match("papar", false, "paper").matches(), "not edit distance");
        check(!match("^foo", false, "foo").matches(), "literal query, not parsed Nucleo syntax");
        check(match("", false, "anything").matches(), "empty query");
        var rows = List.of("x_open_open", "unrelated", "open", "x_open_open");
        var adapter = SFMExplorerNucleoAdapter.compile("open", false);
        check(rows.stream().filter(row -> adapter.match(row, work -> {}).matches()).toList()
                .equals(List.of("x_open_open", "open", "x_open_open")), "eligible input order and duplicate identities retained");
        for (String invalid : List.of("\ud800", "\udc00")) {
            try { match("a", false, invalid); throw new AssertionError("invalid UTF16 accepted"); }
            catch (IllegalArgumentException expected) { checks++; }
        }
        var opts = SFMTextMatchOptions.defaults().withCase(true).withDotAll(true);
        check(opts.toggleNucleo().mode() == SFMTextMatchOptions.Mode.NUCLEO
                && opts.toggleNucleo().toggleNucleo().equals(opts), "optional mode preserves other flags and default");
        check(opts.toggleFuzzy().mode() == SFMTextMatchOptions.Mode.FUZZY
                && opts.toggleRegex().mode() == SFMTextMatchOptions.Mode.REGEX, "existing mode toggles retained");
        var jobs = new ArrayList<Runnable>();
        var latest = new SFMLatestSearchTask<Context, String>(jobs::add);
        Context first = new Context("iron", false, "old", "name", 0, 1);
        List<Context> changed = List.of(new Context("iron", true, "old", "name", 0, 1),
                new Context("iron", false, "new", "name", 0, 1), new Context("iron", false, "old", "icon", 0, 1),
                new Context("iron", false, "old", "name", 1, 1), new Context("iron", false, "old", "name", 0, 2));
        for (Context second : changed) {
            latest.cancel(); jobs.clear();
            latest.submit(first, cancelled -> "old");
            check(latest.result(first).isEmpty(), "pending publishes no invented result");
            latest.submit(second, cancelled -> "new");
            jobs.get(1).run(); jobs.get(0).run();
            check(latest.result(first).isEmpty() && latest.result(second).orElseThrow().value().equals("new"), "full-context stale completion rejected");
        }
        // Old computation is already executing when a newer context is installed and completed.
        var pool = Executors.newSingleThreadExecutor();
        try {
            var entered = new CountDownLatch(1); var proceed = new CountDownLatch(1);
            var during = new SFMLatestSearchTask<Context, String>(pool);
            var observedCancellation = new AtomicBoolean();
            during.submit(first, cancelled -> {
                entered.countDown();
                try { if (!proceed.await(5, TimeUnit.SECONDS)) throw new AssertionError("gate timeout"); }
                catch (InterruptedException e) { throw new CancellationException(); }
                observedCancellation.set(cancelled.getAsBoolean());
                return "obsolete";
            });
            check(entered.await(5, TimeUnit.SECONDS), "worker entered");
            during.close(); proceed.countDown(); pool.shutdown();
            check(pool.awaitTermination(5, TimeUnit.SECONDS) && observedCancellation.get()
                    && during.result(first).isEmpty(), "close cancels running work and rejects late publication");
        } finally { pool.shutdownNow(); }
        var parallel = Executors.newFixedThreadPool(2);
        try {
            var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var finished = new CountDownLatch(1);
            var racing = new SFMLatestSearchTask<Context, String>(task -> parallel.execute(() -> { task.run(); finished.countDown(); }));
            var observed = new AtomicBoolean();
            racing.submit(first, cancelled -> {
                entered.countDown();
                try { if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("old worker timeout"); }
                catch (InterruptedException e) { throw new CancellationException(); }
                observed.set(cancelled.getAsBoolean()); return "late old result";
            });
            check(entered.await(5,TimeUnit.SECONDS), "superseded worker is already running");
            Context second = changed.get(0);
            racing.submit(second,cancelled -> "new result");
            check(finished.await(5,TimeUnit.SECONDS) && racing.result(second).orElseThrow().value().equals("new result"),
                    "new worker can publish before old work finishes");
            release.countDown(); parallel.shutdown();
            check(parallel.awaitTermination(5,TimeUnit.SECONDS) && observed.get() && racing.result(first).isEmpty()
                    && racing.result(second).orElseThrow().value().equals("new result"), "late running completion cannot overwrite newer context");
            racing.close();
        } finally { parallel.shutdownNow(); }
        latest.close();
        check(latest.result(changed.get(4)).isEmpty(), "closure discards retained result");
        // Witnesses from reviewed upstream Rust evidence; this run exercises the Java port.
        var golden = new NucleoMatcher("m:ii").match("minecraft:iron_ingot");
        check(golden.score() == 85 && golden.codePointPositions().equals(List.of(0, 9, 10, 15)), "pinned Rust golden witness");
        System.out.println("PASS " + checks + " real Nucleo binding / context cancellation checks; UI and fresh Rust parity not run here");
    }
}
