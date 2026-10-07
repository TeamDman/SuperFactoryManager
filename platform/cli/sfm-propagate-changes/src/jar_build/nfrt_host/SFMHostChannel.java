package ca.teamdman.sfm.toolchain.nfrt;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/** Invocation-bound request channel. Runtime logs must use a different stream. */
final class SFMHostChannel {
    private static final int MAX_FRAME_BYTES = 4 * 1024 * 1024;
    private final InputStream input;
    private final OutputStream output;
    private final String contractIdentity;
    private long sequence;
    private boolean graphReady;
    private boolean complete;
    private boolean failed;

    SFMHostChannel(InputStream input, OutputStream output, String contractIdentity) {
        if (input == null || output == null || contractIdentity == null
                || !contractIdentity.matches("sha256:[a-f0-9]{64}")) {
            throw new IllegalArgumentException("Invalid host channel identity or streams");
        }
        this.input = input;
        this.output = output;
        this.contractIdentity = contractIdentity;
    }

    String contractIdentity() { return contractIdentity; }

    synchronized String exchange(String operation, String payload) throws IOException {
        if (complete || failed || sequence >= 4096 || payload == null
                || !(operation.equals("graph") && !graphReady
                    || operation.equals("seal") && graphReady
                    || operation.equals("tool") && graphReady
                    || operation.equals("complete") && graphReady)) {
            throw new IOException("Invalid host operation or lifecycle");
        }
        var request = new JsonObject();
        request.addProperty("schema", "sfm:nfrt_host_request@1");
        request.addProperty("contract_identity", contractIdentity);
        request.addProperty("sequence", sequence + 1);
        request.addProperty("operation", operation);
        request.addProperty("payload", payload);
        byte[] bytes = request.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_FRAME_BYTES) throw new IOException("Oversized host request");
        failed = true; // A partial request or invalid reply cannot be retried on this stream.
        output.write(bytes);
        output.write('\n');
        output.flush();
        var response = JsonParser.parseString(readFrame()).getAsJsonObject();
        if (!text(response, "schema").equals("sfm:nfrt_host_response@1")
                || !text(response, "contract_identity").equals(contractIdentity)
                || !text(response, "operation").equals(operation)
                || !response.has("sequence") || !response.get("sequence").isJsonPrimitive()
                || !response.get("sequence").getAsJsonPrimitive().isNumber()
                || !response.get("sequence").getAsString().equals(Long.toString(sequence + 1))) {
            throw new IOException("Foreign, replayed or malformed host response");
        }
        String result = text(response, "payload");
        failed = false;
        sequence++;
        if (operation.equals("graph")) graphReady = true;
        if (operation.equals("complete")) complete = true;
        return result;
    }

    private String readFrame() throws IOException {
        var buffer = new ByteArrayOutputStream();
        for (int count = 0; count <= MAX_FRAME_BYTES; count++) {
            int next = input.read();
            if (next < 0) throw new IOException("Host closed an incomplete response frame");
            if (next == '\n') {
                if (buffer.size() == 0) throw new IOException("Empty host response");
                return StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(buffer.toByteArray())).toString();
            }
            if (count == MAX_FRAME_BYTES) throw new IOException("Oversized host response");
            buffer.write(next);
        }
        throw new IOException("Missing host response terminator");
    }

    private static String text(JsonObject object, String key) throws IOException {
        var field = object.get(key);
        if (field == null || !field.isJsonPrimitive() || !field.getAsJsonPrimitive().isString()) {
            throw new IOException("Malformed host string field: " + key);
        }
        return field.getAsString();
    }
}
