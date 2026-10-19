package ca.teamdman.sfm.toolchain.nfrt;

import com.google.gson.JsonObject;
import java.io.PrintStream;

/** Observation only: bounded JSON lines on stderr, never host protocol stdout.
 * Durations are nested wall time from System.nanoTime, never process CPU time.
 */
public final class SFMHostTiming implements AutoCloseable {
    private final String phase;
    private final String node;
    private final long started;
    private final PrintStream sink;
    private boolean succeeded;
    private boolean closed;
    private long items = -1;
    private long bytes = -1;

    private SFMHostTiming(String phase, String node) {
        this.phase = bounded(phase, 64);
        this.node = bounded(node, 128);
        started = System.nanoTime();
        sink = System.err;
    }

    static SFMHostTiming start(String phase, String node) {
        return new SFMHostTiming(phase, node);
    }

    static void declaredNode(String node, String action) {
        try {
            var event = new JsonObject();
            event.addProperty("schema", "sfm:nfrt_host_node@1");
            event.addProperty("phase", "node_declared");
            event.addProperty("node", bounded(node, 128));
            event.addProperty("action", bounded(action, 192));
            event.addProperty("execution_state", "declared_only");
            event.addProperty("workspace_policy", "fresh_declared");
            event.addProperty("inherited_intermediate_restore_enabled", false);
            System.err.println(event);
        } catch (RuntimeException ignored) {
            // Declaration observation does not authorize or execute the action.
        }
    }

    // Negative counts mean unavailable, not zero work. No extra input inspection.
    void success(long items, long bytes) {
        this.items = items;
        this.bytes = bytes;
        succeeded = true;
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        long elapsed = Math.max(0, System.nanoTime() - started) / 1_000;
        try {
            var event = new JsonObject();
            event.addProperty("schema", "sfm:nfrt_host_timing@1");
            event.addProperty("phase", phase);
            event.addProperty("node", node);
            event.addProperty("elapsed_us", elapsed);
            event.addProperty("outcome", succeeded ? "success" : "failed");
            event.addProperty("work_items", Math.max(0, items));
            event.addProperty("work_items_known", items >= 0);
            event.addProperty("work_bytes", Math.max(0, bytes));
            event.addProperty("work_bytes_known", bytes >= 0);
            event.addProperty("measurement", "wall_duration");
            event.addProperty("nested", true);
            event.addProperty("duration_scope", switch (phase) {
                case "run_node" -> "requested_node_and_predecessors";
                case "tool_channel_roundtrip", "seal" -> "rust_host_roundtrip_and_validation";
                case "javac_recompile" -> "in_process_compiler_task";
                default -> "phase_wall";
            });
            sink.println(event);
        } catch (RuntimeException ignored) {
            // Diagnostics cannot change preparation success/failure or protocol state.
        }
    }

    private static String bounded(String value, int limit) {
        if (value == null) return "";
        if (value.length() <= limit) return value;
        int end = Character.isHighSurrogate(value.charAt(limit - 1)) ? limit - 1 : limit;
        return value.substring(0, end);
    }
}
