package ca.teamdman.sfm.client.control;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;

/**
 * Small opt-in request-file bridge for a running client. Requests are retained
 * as evidence; each sequence is consumed at most once and responses are
 * atomically published beside it.
 */
final class SFMClientControlFileBridge {
    static final int MAX_REQUEST_BYTES = 64 * 1024;
    static final int MAX_SEQUENCE = 1_000_000;

    private final Path directory;
    private final Function<String, JsonObject> executor;
    private int sequence = 1;

    private SFMClientControlFileBridge(Path directory, Function<String, JsonObject> executor) {
        this.directory = directory;
        this.executor = executor;
    }

    static SFMClientControlFileBridge open(Path directory, Function<String, JsonObject> executor) throws IOException {
        Files.createDirectories(directory);
        SFMClientControlFileBridge bridge = new SFMClientControlFileBridge(
                directory.toAbsolutePath().normalize(), Objects.requireNonNull(executor));
        JsonObject ready = new JsonObject();
        ready.addProperty("schema", "sfm.client-control-files/1");
        ready.addProperty("request_pattern", "%06d.request.json");
        ready.addProperty("response_pattern", "%06d.response.json");
        ready.addProperty("command_field", "command");
        ready.addProperty("directory", bridge.directory.toString());
        bridge.writeAtomically(bridge.directory.resolve("ready.json"), ready.toString());
        return bridge;
    }

    Path directory() {
        return directory;
    }

    void poll() {
        if (sequence > MAX_SEQUENCE) return;
        Path request = directory.resolve(String.format(Locale.ROOT, "%06d.request.json", sequence));
        if (!Files.isRegularFile(request, LinkOption.NOFOLLOW_LINKS)) return;
        JsonObject response = new JsonObject();
        response.addProperty("schema", "sfm.client-control-response/1");
        response.addProperty("sequence", sequence);
        try {
            if (Files.size(request) > MAX_REQUEST_BYTES) throw new IOException("request exceeds byte limit");
            JsonObject input = JsonParser.parseString(Files.readString(request, StandardCharsets.UTF_8)).getAsJsonObject();
            String command = input.has("command") ? input.get("command").getAsString() : "";
            if (command.isBlank() && input.has("action_tokens") && input.get("action_tokens").isJsonArray()) {
                StringBuilder joined = new StringBuilder("sfm action invoke");
                for (var value : input.getAsJsonArray("action_tokens")) {
                    if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IOException("action token must be a string");
                    joined.append(' ').append(SFMClientControlServer.escapeCommandToken(value.getAsString()));
                }
                command = joined.toString();
            }
            if (command.isBlank()) throw new IOException("request needs a command or action_tokens array");
            JsonObject execution = executor.apply(command);
            for (var entry : execution.entrySet()) response.add(entry.getKey(), entry.getValue());
            response.addProperty("status", execution.has("error") ? "error" : "completed");
        } catch (Exception failure) {
            response.addProperty("status", "error");
            response.addProperty("error", failure.toString());
        }
        try {
            writeAtomically(directory.resolve(String.format(Locale.ROOT, "%06d.response.json", sequence)), response.toString());
        } catch (IOException failure) {
            response.addProperty("status", "error");
            response.addProperty("error", "response publication failed: " + failure);
        }
        sequence++;
    }

    private void writeAtomically(Path destination, String contents) throws IOException {
        Path temporary = destination.resolveSibling(destination.getFileName() + ".tmp");
        Files.writeString(temporary, contents, StandardCharsets.UTF_8);
        try {
            Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
            Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
