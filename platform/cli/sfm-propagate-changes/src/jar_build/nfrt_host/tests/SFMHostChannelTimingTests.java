package ca.teamdman.sfm.toolchain.nfrt;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Bounded real-thread fixture; no host, filesystem input, external tool or Minecraft. */
public final class SFMHostChannelTimingTests {
    private static final String IDENTITY = "sha256:" + "0".repeat(64);

    public static void main(String[] args) throws Exception {
        var stderr = new ByteArrayOutputStream();
        var stdout = new ByteArrayOutputStream();
        var originalErr = System.err;
        var originalOut = System.out;
        try (var errors = new PrintStream(stderr, true, StandardCharsets.UTF_8);
             var output = new PrintStream(stdout, true, StandardCharsets.UTF_8)) {
            System.setErr(errors);
            System.setOut(output);
            try {
                contendedExchange(stderr);
                malformedReplyRemainsFailed();
            } finally {
                System.setErr(originalErr);
                System.setOut(originalOut);
            }
        }
        if (stdout.size() != 0) throw new AssertionError("Timing polluted protocol stdout");
        var lines = stderr.toString(StandardCharsets.UTF_8).lines().toList();
        if (lines.size() != 6) throw new AssertionError("Missing or duplicated acquisition event");
        for (String line : lines) {
            var event = JsonParser.parseString(line).getAsJsonObject();
            if (!event.get("schema").getAsString().equals("sfm:nfrt_host_timing@1")
                    || !event.get("phase").getAsString().equals("channel_monitor_wait")
                    || !event.get("node").getAsString().isEmpty()
                    || !event.get("elapsed_us").getAsJsonPrimitive().isNumber()
                    || event.get("elapsed_us").getAsLong() < 0
                    || !event.get("outcome").getAsString().equals("success")
                    || event.get("work_items").getAsLong() != 1
                    || !event.get("work_items_known").getAsBoolean()
                    || event.get("work_bytes").getAsLong() != 0
                    || event.get("work_bytes_known").getAsBoolean()
                    || !event.get("measurement").getAsString().equals("wall_duration")
                    || !event.get("nested").getAsBoolean()
                    || !event.get("duration_scope").getAsString().equals("phase_wall")) {
                throw new AssertionError("Invalid acquisition observation");
            }
        }
        originalErr.print(stderr.toString(StandardCharsets.UTF_8));
        System.out.println("PASS bounded two-thread monitor acquisition, exact protocol and lifecycle");
    }

    private static void contendedExchange(ByteArrayOutputStream stderr) throws Exception {
        var firstRead = new CountDownLatch(1);
        var releaseReply = new CountDownLatch(1);
        var firstDone = new CountDownLatch(1);
        var secondAttempt = new CountDownLatch(1);
        var secondDone = new CountDownLatch(1);
        var failure = new AtomicReference<Throwable>();
        var gateRead = new AtomicBoolean();
        var replies = new ByteArrayInputStream((frame("response", 1, "graph", "graph-result")
                + frame("response", 2, "tool", "tool-result")
                + frame("response", 3, "complete", "complete-result")).getBytes(StandardCharsets.UTF_8));
        var input = new InputStream() {
            @Override public int read() throws IOException {
                if (gateRead.compareAndSet(false, true)) {
                    firstRead.countDown();
                    try {
                        if (!releaseReply.await(10, TimeUnit.SECONDS)) {
                            throw new IOException("Fixture response gate timed out");
                        }
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Fixture response gate interrupted", interrupted);
                    }
                }
                return replies.read();
            }
        };
        var wire = new ByteArrayOutputStream();
        var channel = new SFMHostChannel(input, wire, IDENTITY);
        var first = new Thread(() -> {
            try {
                if (!channel.exchange("graph", "graph-input").equals("graph-result")) {
                    throw new AssertionError("Graph result changed");
                }
            } catch (Throwable caught) {
                failure.compareAndSet(null, caught);
            } finally {
                firstDone.countDown();
            }
        }, "channel-first");
        var second = new Thread(() -> {
            try {
                secondAttempt.countDown();
                if (!channel.exchange("tool", "tool-input").equals("tool-result")) {
                    throw new AssertionError("Tool result changed");
                }
            } catch (Throwable caught) {
                failure.compareAndSet(null, caught);
            } finally {
                secondDone.countDown();
            }
        }, "channel-second");
        // Daemon status and finite input wait also bound failure cleanup.
        first.setDaemon(true);
        second.setDaemon(true);
        first.start();
        try {
            await(firstRead, "First exchange did not reach response read");
            if (stderr.toString(StandardCharsets.UTF_8).lines().count() != 1) {
                throw new AssertionError("Acquisition event was not closed before response work");
            }
            second.start();
            await(secondAttempt, "Second caller did not attempt exchange");
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (second.getState() != Thread.State.BLOCKED && second.isAlive()
                    && System.nanoTime() < deadline) {
                Thread.yield();
            }
            if (second.getState() != Thread.State.BLOCKED) {
                throw new AssertionError("Second caller did not block on channel monitor");
            }
            if (stderr.toString(StandardCharsets.UTF_8).lines().count() != 1
                    || !wire.toString(StandardCharsets.UTF_8).equals(
                            frame("request", 1, "graph", "graph-input"))) {
                throw new AssertionError("Second caller entered protocol work before acquisition");
            }
        } finally {
            releaseReply.countDown();
            await(firstDone, "First caller did not finish");
            if (second.getState() != Thread.State.NEW) await(secondDone, "Second caller did not finish");
            first.join(1_000);
            if (second.getState() != Thread.State.NEW) second.join(1_000);
        }
        if (first.isAlive() || second.isAlive()) throw new AssertionError("Fixture thread leaked");
        if (failure.get() != null) throw new AssertionError("Exchange worker failed", failure.get());
        if (!channel.exchange("complete", "complete-input").equals("complete-result")) {
            throw new AssertionError("Completion result changed");
        }
        String expected = frame("request", 1, "graph", "graph-input")
                + frame("request", 2, "tool", "tool-input")
                + frame("request", 3, "complete", "complete-input");
        if (!wire.toString(StandardCharsets.UTF_8).equals(expected)) {
            throw new AssertionError("Concurrent request frames or sequence changed");
        }
        try {
            channel.exchange("tool", "after-complete");
            throw new AssertionError("Completed channel accepted another exchange");
        } catch (IOException expectedFailure) {
            if (!expectedFailure.getMessage().equals("Invalid host operation or lifecycle")) throw expectedFailure;
        }
        if (!wire.toString(StandardCharsets.UTF_8).equals(expected)) {
            throw new AssertionError("Completed channel wrote another frame");
        }
    }

    private static void malformedReplyRemainsFailed() throws IOException {
        var wire = new ByteArrayOutputStream();
        var input = new ByteArrayInputStream(frame("response", 9, "graph", "wrong-sequence")
                .getBytes(StandardCharsets.UTF_8));
        var channel = new SFMHostChannel(input, wire, IDENTITY);
        try {
            channel.exchange("graph", "malformed-input");
            throw new AssertionError("Malformed reply accepted");
        } catch (IOException expectedFailure) {
            if (!expectedFailure.getMessage().equals("Foreign, replayed or malformed host response")) throw expectedFailure;
        }
        try {
            channel.exchange("graph", "retry");
            throw new AssertionError("Failed channel retried a partial request");
        } catch (IOException expectedFailure) {
            if (!expectedFailure.getMessage().equals("Invalid host operation or lifecycle")) throw expectedFailure;
        }
        if (!wire.toString(StandardCharsets.UTF_8).equals(frame("request", 1, "graph", "malformed-input"))) {
            throw new AssertionError("Failed channel wrote a retry frame");
        }
    }

    private static void await(CountDownLatch latch, String message) throws InterruptedException {
        if (!latch.await(10, TimeUnit.SECONDS)) throw new AssertionError(message);
    }

    private static String frame(String kind, long sequence, String operation, String payload) {
        var frame = new JsonObject();
        frame.addProperty("schema", "sfm:nfrt_host_" + kind + "@1");
        frame.addProperty("contract_identity", IDENTITY);
        frame.addProperty("sequence", sequence);
        frame.addProperty("operation", operation);
        frame.addProperty("payload", payload);
        return frame + "\n";
    }
}
