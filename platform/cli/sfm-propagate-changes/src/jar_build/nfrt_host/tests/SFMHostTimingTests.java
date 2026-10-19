package ca.teamdman.sfm.toolchain.nfrt;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/** Pure fixture: no graph, filesystem payload, external tool or Minecraft run. */
public final class SFMHostTimingTests {
    public static void main(String[] args) throws Exception {
        var stderr = new ByteArrayOutputStream();
        var stdout = new ByteArrayOutputStream();
        var originalErr = System.err;
        var originalOut = System.out;
        String node = "escaped\"\n\\node";
        try (var errors = new PrintStream(stderr, true, StandardCharsets.UTF_8);
             var output = new PrintStream(stdout, true, StandardCharsets.UTF_8)) {
            System.setErr(errors);
            System.setOut(output);
            try {
                try (var timing = SFMHostTiming.start("graph_load", node)) {
                    timing.success(7, 11);
                }
                try {
                    try (var timing = SFMHostTiming.start("javac_recompile", "failure")) {
                        throw new IOException("fixture failure");
                    }
                } catch (IOException expected) {
                    if (!expected.getMessage().equals("fixture failure")) throw expected;
                }
                var once = SFMHostTiming.start("x".repeat(1_000), "y".repeat(1_000));
                once.success(-1, -1);
                once.close();
                once.close();
                protocolUnchanged();
                SFMHostTiming.declaredNode(node, "action".repeat(100));
            } finally {
                System.setErr(originalErr);
                System.setOut(originalOut);
            }
        }
        if (stdout.size() != 0) throw new AssertionError("Timing polluted protocol stdout");
        var lines = stderr.toString(StandardCharsets.UTF_8).lines().toList();
        if (lines.size() != 5) throw new AssertionError("Missing, duplicate or multiline timing event");
        var success = JsonParser.parseString(lines.get(0)).getAsJsonObject();
        var failed = JsonParser.parseString(lines.get(1)).getAsJsonObject();
        var bounded = JsonParser.parseString(lines.get(2)).getAsJsonObject();
        for (String line : lines.subList(0, 3)) {
            var event = JsonParser.parseString(line).getAsJsonObject();
            if (!event.get("schema").getAsString().equals("sfm:nfrt_host_timing@1")
                    || event.get("elapsed_us").getAsLong() < 0
                    || !event.get("measurement").getAsString().equals("wall_duration")
                    || !event.get("nested").getAsBoolean()) throw new AssertionError("Invalid timing contract");
        }
        var channelWait = JsonParser.parseString(lines.get(3)).getAsJsonObject();
        if (!channelWait.get("schema").getAsString().equals("sfm:nfrt_host_timing@1")
                || !channelWait.get("phase").getAsString().equals("channel_monitor_wait")
                || !channelWait.get("node").getAsString().isEmpty()
                || !channelWait.get("elapsed_us").getAsJsonPrimitive().isNumber()
                || channelWait.get("elapsed_us").getAsLong() < 0
                || !channelWait.get("outcome").getAsString().equals("success")
                || channelWait.get("work_items").getAsLong() != 1
                || !channelWait.get("work_items_known").getAsBoolean()
                || channelWait.get("work_bytes").getAsLong() != 0
                || channelWait.get("work_bytes_known").getAsBoolean()
                || !channelWait.get("measurement").getAsString().equals("wall_duration")
                || !channelWait.get("nested").getAsBoolean()
                || !channelWait.get("duration_scope").getAsString().equals("phase_wall")) {
            throw new AssertionError("Channel acquisition observation changed");
        }
        var declaration = JsonParser.parseString(lines.get(4)).getAsJsonObject();
        if (!declaration.get("schema").getAsString().equals("sfm:nfrt_host_node@1")
                || !declaration.get("node").getAsString().equals(node)
                || declaration.get("action").getAsString().length() != 192
                || !declaration.get("execution_state").getAsString().equals("declared_only")
                || declaration.get("inherited_intermediate_restore_enabled").getAsBoolean())
            throw new AssertionError("Declaration observation changed");
        if (!success.get("node").getAsString().equals(node)
                || !success.get("outcome").getAsString().equals("success")
                || success.get("work_items").getAsLong() != 7
                || success.get("work_bytes").getAsLong() != 11
                || !success.get("work_items_known").getAsBoolean()
                || !success.get("work_bytes_known").getAsBoolean()) throw new AssertionError("Escaping/counts changed");
        if (!failed.get("outcome").getAsString().equals("failed")
                || failed.get("work_items_known").getAsBoolean()
                || bounded.get("phase").getAsString().length() != 64
                || bounded.get("node").getAsString().length() != 128
                || bounded.get("work_items_known").getAsBoolean()
                || bounded.get("work_bytes_known").getAsBoolean()) throw new AssertionError("Failure/bounds/unknown changed");
        System.out.println("PASS timing JSON escaping, bounds, outcomes, counts, idempotence and protocol isolation");
    }

    private static void protocolUnchanged() throws IOException {
        String identity = "sha256:" + "0".repeat(64);
        var response = new JsonObject();
        response.addProperty("schema", "sfm:nfrt_host_response@1");
        response.addProperty("contract_identity", identity);
        response.addProperty("sequence", 1);
        response.addProperty("operation", "graph");
        response.addProperty("payload", "{}");
        var wire = new ByteArrayOutputStream();
        var channel = new SFMHostChannel(new ByteArrayInputStream(
                (response + "\n").getBytes(StandardCharsets.UTF_8)), wire, identity);
        if (!channel.exchange("graph", "{}").equals("{}")) throw new AssertionError("Protocol reply changed");
        var expected = new JsonObject();
        expected.addProperty("schema", "sfm:nfrt_host_request@1");
        expected.addProperty("contract_identity", identity);
        expected.addProperty("sequence", 1);
        expected.addProperty("operation", "graph");
        expected.addProperty("payload", "{}");
        if (!wire.toString(StandardCharsets.UTF_8).equals(expected + "\n")) throw new AssertionError("Protocol bytes changed");
    }
}
