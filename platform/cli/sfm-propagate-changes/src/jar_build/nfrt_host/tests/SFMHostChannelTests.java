package ca.teamdman.sfm.toolchain.nfrt;

import com.google.gson.JsonObject;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Pure transport checks; no process, filesystem or execution authority. */
public final class SFMHostChannelTests {
    private static final String ID = "sha256:" + "0".repeat(64);

    public static void main(String[] args) throws Exception {
        var output = new ByteArrayOutputStream();
        var channel = channel(response(1, "graph", ID) + response(2, "seal", ID)
                + response(3, "complete", ID), output);
        refuse(() -> channel.exchange("seal", "{}"));
        if (output.size() != 0) throw new AssertionError("Invalid lifecycle wrote request bytes");
        channel.exchange("graph", "{\"source\":\"line1\\nline2 λ\"}");
        channel.exchange("seal", "[]");
        channel.exchange("complete", "{}");
        refuse(() -> channel.exchange("seal", "[]"));
        if (output.toString(StandardCharsets.UTF_8).lines().count() != 3) {
            throw new AssertionError("Payload newlines broke framing");
        }
        for (String invalid : new String[] {"", "{}", response(2, "graph", ID),
                response(1, "seal", ID), response(1, "graph", "sha256:" + "1".repeat(64)),
                "x".repeat(4 * 1024 * 1024 + 1)}) {
            var broken = channel(invalid, new ByteArrayOutputStream());
            refuse(() -> broken.exchange("graph", "{}"));
            refuse(() -> broken.exchange("graph", "{}"));
        }
        var malformed = new SFMHostChannel(new ByteArrayInputStream(new byte[] {(byte)0xff, '\n'}),
                new ByteArrayOutputStream(), ID);
        refuse(() -> malformed.exchange("graph", "{}"));
        System.out.println("PASS host channel: lifecycle, sequence, contract, UTF-8 and size refusal; poisoned stream refusal; escaped framing");
    }

    private static SFMHostChannel channel(String input, ByteArrayOutputStream output) {
        return new SFMHostChannel(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)), output, ID);
    }
    private static String response(long sequence, String operation, String identity) {
        var response = new JsonObject();
        response.addProperty("schema", "sfm:nfrt_host_response@1");
        response.addProperty("contract_identity", identity);
        response.addProperty("sequence", sequence);
        response.addProperty("operation", operation);
        response.addProperty("payload", "{}");
        return response + "\n";
    }
    @FunctionalInterface private interface Attempt { void run() throws IOException; }
    private static void refuse(Attempt operation) throws IOException {
        try { operation.run(); } catch (IOException expected) { return; }
        throw new AssertionError("Invalid host exchange accepted");
    }
}
