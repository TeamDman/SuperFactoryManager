package ca.teamdman.sfm.toolchain.nfrt;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Connect exact completed graph requests to the Rust-owned sealing service. */
final class SFMHostProducerSealer implements SFMFreshProducerBindings.Sealer {
    private final SFMHostChannel channel;
    private final Path root;

    SFMHostProducerSealer(SFMHostChannel channel, Path root) {
        if (channel == null || root == null || !root.isAbsolute() || !root.normalize().equals(root)) {
            throw new IllegalArgumentException("Sealer requires the owned absolute invocation root");
        }
        this.channel = channel;
        this.root = root;
    }

    @Override
    public List<SFMFreshProducerBindings.Snapshot> seal(List<SFMFreshProducerBindings.Request> requests)
            throws IOException {
        if (requests.size() > 256) throw new IOException("Oversized producer request batch");
        var payload = new JsonArray();
        for (var request : requests) {
            if (request.originalInput() || !request.outputPath().startsWith(root.resolve("work"))) {
                throw new IOException("Producer request is not a declared fresh work output");
            }
            var row = new JsonObject();
            row.addProperty("producer_id", request.producerId());
            row.addProperty("output_id", request.outputId());
            row.addProperty("output_type", request.outputType());
            row.addProperty("output_relative_path", relative(request.outputPath()));
            payload.add(row);
        }
        var response = JsonParser.parseString(channel.exchange("seal", payload.toString())).getAsJsonArray();
        if (response.size() != requests.size()) throw new IOException("Sealer changed consumer cardinality");
        var snapshots = new ArrayList<SFMFreshProducerBindings.Snapshot>(requests.size());
        for (int index = 0; index < requests.size(); index++) {
            var request = requests.get(index);
            var row = response.get(index).getAsJsonObject();
            var declaration = row.getAsJsonObject("declaration");
            var scope = row.getAsJsonObject("scope");
            if (!text(row, "schema").equals("sfm:nfrt_fresh_producer_output@1")
                    || declaration == null
                    || scope == null
                    || !text(scope, "prepared_contract_sha256").equals(channel.contractIdentity())
                    || !text(declaration, "producer_id").equals(request.producerId())
                    || !text(declaration, "output_id").equals(request.outputId())
                    || !text(declaration, "output_type").equals(request.outputType())
                    || !text(declaration, "output_relative_path").equals(relative(request.outputPath()))) {
                throw new IOException("Sealer changed completed producer identity/order");
            }
            for (String flag : List.of("completion_was_fresh", "inherited_cache_restore_disabled", "memory_snapshot_immutable")) {
                if (!row.has(flag) || !row.get(flag).isJsonPrimitive()
                        || !row.get(flag).getAsJsonPrimitive().isBoolean() || !row.get(flag).getAsBoolean()) {
                    throw new IOException("Sealed output lacks its immutable fresh-producer contract");
                }
            }
            String snapshot = text(row, "snapshot_relative_path");
            String digest = text(row, "full_sha256");
            if (!snapshot.matches("inputs/generated/[0-9]{4}\\.bin")
                    || !digest.matches("sha256:[a-f0-9]{64}")
                    || !row.has("bytes") || !row.get("bytes").isJsonPrimitive()
                    || !row.get("bytes").getAsJsonPrimitive().isNumber()
                    || !row.get("bytes").getAsString().matches("[0-9]+")) {
                throw new IOException("Invalid sealed snapshot metadata");
            }
            long bytes = row.get("bytes").getAsLong();
            if (bytes <= 0 || bytes > 512L * 1024L * 1024L) throw new IOException("Invalid sealed snapshot size");
            snapshots.add(new SFMFreshProducerBindings.Snapshot(request, root.resolve(snapshot), bytes,
                    digest.substring("sha256:".length())));
        }
        return List.copyOf(snapshots);
    }

    private String relative(Path path) { return root.relativize(path).toString().replace('\\', '/'); }

    private static String text(JsonObject object, String key) throws IOException {
        var field = object.get(key);
        if (field == null || !field.isJsonPrimitive() || !field.getAsJsonPrimitive().isString()) {
            throw new IOException("Malformed producer response field: " + key);
        }
        return field.getAsString();
    }
}
