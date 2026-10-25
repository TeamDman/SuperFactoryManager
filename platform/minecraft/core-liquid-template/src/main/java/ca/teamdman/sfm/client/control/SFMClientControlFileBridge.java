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
import java.util.concurrent.CompletableFuture;

/**
 * Small opt-in request-file bridge for a running client. Requests are retained
 * as evidence; each sequence is consumed at most once and responses are
 * atomically published beside it.
 */
final class SFMClientControlFileBridge implements AutoCloseable {
    interface AsyncExecutor { CompletableFuture<JsonObject> execute(String command); }
    static final int MAX_REQUEST_BYTES = 64 * 1024;
    static final int MAX_SEQUENCE = 1_000_000;

    private final Path directory;
    private final AsyncExecutor executor;
    private CompletableFuture<JsonObject> pending;
    private JsonObject response;
    private boolean closed;
    private IOException publicationFailure;
    private int sequence = 1;

    private SFMClientControlFileBridge(Path directory, AsyncExecutor executor) {
        this.directory = directory;
        this.executor = executor;
    }

    static SFMClientControlFileBridge open(Path directory, Function<String, JsonObject> executor) throws IOException {
        return openAsync(directory, command -> CompletableFuture.completedFuture(executor.apply(command)));
    }

    static SFMClientControlFileBridge openAsync(Path directory, AsyncExecutor executor) throws IOException {
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

    /** Called by the service's bounded I/O worker, never by the client/render thread. */
    synchronized void poll() {
        if (closed || sequence > MAX_SEQUENCE) return;
        if (response == null) {
            Path request = directory.resolve(String.format(Locale.ROOT, "%06d.request.json", sequence));
            if (!Files.isRegularFile(request, LinkOption.NOFOLLOW_LINKS)) return;
            response = new JsonObject();
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
                pending = executor.execute(command);
            } catch (Exception failure) {
                response.addProperty("status", "error");
                response.addProperty("error", failure.toString());
            }
        }
        if (pending != null) {
            if (!pending.isDone()) return;
            try {
                JsonObject execution = pending.join();
                for (var entry : execution.entrySet()) response.add(entry.getKey(), entry.getValue());
                response.addProperty("status", execution.has("error") ? "error" : "completed");
            } catch (Exception failure) {
                response.addProperty("status", "error");
                response.addProperty("error", failure.toString());
            }
            pending = null;
        }
        try {
            writeAtomically(directory.resolve(String.format(Locale.ROOT, "%06d.response.json", sequence)), response.toString());
            publicationFailure = null;
            response = null;
            sequence++;
        } catch (IOException failure) {
            // Keep the exact completed receipt. A later poll retries publication,
            // not the action; a failed disk write must never duplicate a mutation.
            publicationFailure = failure;
        }
    }

    synchronized IOException publicationFailure() { return publicationFailure; }

    @Override
    public synchronized void close() {
        closed = true;
        if (response != null) {
            if (pending != null) {
                if (pending.isDone() && !pending.isCompletedExceptionally()) {
                    JsonObject execution = pending.join();
                    for (var entry : execution.entrySet()) response.add(entry.getKey(), entry.getValue());
                    response.addProperty("status", execution.has("error") ? "error" : "completed");
                } else {
                    pending.cancel(false);
                    response.addProperty("status", "interrupted");
                    response.addProperty("error", "transport disabled; an admitted action may already have executed; do not retry blindly");
                    response.addProperty("outcome", "unknown");
                }
                pending = null;
            }
            try {
                writeAtomically(directory.resolve(String.format(Locale.ROOT, "%06d.response.json", sequence)), response.toString());
                response = null;
            } catch (IOException failure) { publicationFailure = failure; }
        }
        try {
            Files.deleteIfExists(directory.resolve("ready.json"));
        } catch (IOException failure) {
            publicationFailure = failure;
        }
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
