package ca.teamdman.sfm.gametest.puppet;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;

/** Bounded, opt-in file control for the isolated two-process puppet, not a game protocol. */
public final class SFMMultiplayerPuppetControl {
    public static final String RUN_PROPERTY = "sfm.multiplayerPuppet.runId";
    public static final int MAX_BYTES = 16_384;
    public static final int MAX_REQUESTS = 256;

    private final Path directory;
    private final UUID runId;
    private final int port;

    private SFMMultiplayerPuppetControl(Path directory, UUID runId, int port) {
        this.directory = directory;
        this.runId = runId;
        this.port = port;
    }

    public static boolean enabled() {
        return System.getProperty(RUN_PROPERTY) != null;
    }

    public static SFMMultiplayerPuppetControl open(String role) {
        if (!"server".equals(role) && !"client".equals(role)) throw new IllegalArgumentException("Invalid role");
        UUID runId = UUID.fromString(System.getProperty(RUN_PROPERTY, ""));
        Path working = Path.of("").toAbsolutePath().normalize();
        if (!working.getFileName().toString().equals(role)) {
            throw new IllegalStateException("Multiplayer puppet requires its isolated " + role + " working directory");
        }
        Path root = working.getParent();
        if (!root.getFileName().toString().equals("run-" + runId)) {
            throw new IllegalStateException("Multiplayer puppet working directory does not match its run identity");
        }
        Path directory = root.resolve("control");
        JsonObject manifest = read(directory.resolve("run.json"));
        if (manifest == null || !runId.toString().equals(manifest.get("runId").getAsString())
                || !"127.0.0.1".equals(manifest.get("host").getAsString())) {
            throw new IllegalStateException("Missing or mismatched loopback puppet manifest");
        }
        int port = manifest.get("port").getAsInt();
        if (port < 1024 || port > 65535) throw new IllegalStateException("Invalid loopback puppet port");
        return new SFMMultiplayerPuppetControl(directory, runId, port);
    }

    public int port() {
        return port;
    }

    public String endpoint() {
        return "127.0.0.1:" + port;
    }

    public JsonObject observation() {
        JsonObject result = new JsonObject();
        result.addProperty("runId", runId.toString());
        result.addProperty("endpoint", endpoint());
        return result;
    }

    public Path path(String role, String name) {
        if (!role.matches("server|client") || !name.matches("[a-z0-9.-]+")) {
            throw new IllegalArgumentException("Invalid control artifact name");
        }
        return directory.resolve(role + "-" + name);
    }

    public Path request(String role, int sequence) {
        if (sequence < 1 || sequence > MAX_REQUESTS) throw new IllegalStateException("Puppet request limit exceeded");
        return path(role, String.format(Locale.ROOT, "%06d.request.json", sequence));
    }

    public Path response(String role, int sequence) {
        if (sequence < 1 || sequence > MAX_REQUESTS) throw new IllegalStateException("Puppet response limit exceeded");
        return path(role, String.format(Locale.ROOT, "%06d.response.json", sequence));
    }

    public JsonObject readRequest(String role, int sequence) {
        JsonObject request = read(request(role, sequence));
        if (request != null && (!request.has("runId") || !runId.toString().equals(request.get("runId").getAsString()))) {
            throw new IllegalArgumentException("Puppet request run identity mismatch");
        }
        return request;
    }

    public static JsonObject read(Path path) {
        if (Files.notExists(path, LinkOption.NOFOLLOW_LINKS)) return null;
        try (var input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = input.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) throw new IOException("Puppet JSON exceeds byte limit");
            return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Cannot read bounded puppet request " + path.getFileName(), exception);
        }
    }

    public static void write(Path path, JsonObject value) {
        byte[] bytes = value.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) throw new IllegalArgumentException("Puppet JSON exceeds byte limit");
        try {
            Path temporary = path.resolveSibling(path.getFileName() + ".writing");
            Files.write(temporary, bytes);
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot publish puppet response " + path.getFileName(), exception);
        }
    }
}
