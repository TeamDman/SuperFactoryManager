package ca.teamdman.sfm.gametest.puppet.action;

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% if features.game_puppet_runtime %}
import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** Explicit local file requests in one disposable superflat world. */
public final class ManagedSessionPuppetAction implements SFMPuppetAction {
    private final String defaultTest;
    private final Path directory = Path.of("managed-session").toAbsolutePath().normalize();
    private final long lifetimeSeconds = Long.parseLong(System.getProperty("sfm.managedSession.seconds", "1800"));
    private long started;
    private long requestStarted;
    private int sequence = 1;
    private int actionIndex;
    private List<SFMPuppetAction> pending = List.of();
    private String operation;

    public ManagedSessionPuppetAction(String defaultTest) {
        if (lifetimeSeconds < 1 || lifetimeSeconds > 28800) throw new IllegalArgumentException("Session lifetime must be1..28800 seconds");
        this.defaultTest = defaultTest;
    }

    @Override public String description() { return "managed superflat request " + sequence; }

    @Override public boolean tick(ISFMGamePuppetRuntime runtime) {
        if (started == 0) {
            started = System.nanoTime();
            try { Files.createDirectory(directory); } catch (java.io.IOException error) { throw new IllegalStateException("Fresh managed session directory required", error); }
            JsonObject ready = new JsonObject();
            ready.addProperty("schema", "sfm.managed-session/1");
            ready.addProperty("world", runtime.puppetWorldId());
            ready.addProperty("max_requests", 128);
            ready.addProperty("lifetime_seconds", lifetimeSeconds);
            write(directory.resolve("ready.json"), ready);
            SFM.LOGGER.info("SFM_MANAGED_SESSION_READY world={} directory={}", runtime.puppetWorldId(), directory);
        }
        if (System.nanoTime() - started > lifetimeSeconds * 1_000_000_000L || sequence > 128) return true;
        if (pending.isEmpty()) {
            JsonObject request = read(path("request"));
            if (request == null) return false;
            requestStarted = System.nanoTime();
            try {
                if (!request.has("sequence") || request.get("sequence").getAsInt() != sequence) throw new IllegalArgumentException("Sequence mismatch");
                operation = request.get("op").getAsString();
                if ("stop".equals(operation)) { publish("completed", null); return true; }
                if ("run_game_test".equals(operation) || "reset".equals(operation)) {
                    String test = request.has("game_test") ? request.get("game_test").getAsString() : defaultTest;
                    if (!test.matches("[a-zA-Z0-9_]+")) throw new IllegalArgumentException("Invalid test id");
                    pending = List.of(new RunGameTestPuppetAction(test));
                } else if ("capture".equals(operation)) {
                    double angle = request.has("angle") ? request.get("angle").getAsDouble() : 0;
                    if (!Double.isFinite(angle) || Math.abs(angle) > Math.PI * 2) throw new IllegalArgumentException("Invalid camera angle");
                    pending = List.of(new PositionGameTestOrbitCameraPuppetAction(angle),new CapturePuppetAction(String.format(Locale.ROOT,"session-%06d",sequence),Component.literal("Managed session request " + sequence)));
                } else throw new IllegalArgumentException("Unsupported operation");
                actionIndex = 0;
            } catch (RuntimeException error) { publish("error", error.toString()); return false; }
        }
        try {
            if (System.nanoTime() - requestStarted > 110_000_000_000L) throw new IllegalStateException("Managed request timed out");
            if (pending.get(actionIndex).tick(runtime) && ++actionIndex == pending.size()) publish("completed", null);
        } catch (RuntimeException error) { publish("error", error.toString()); throw error; }
        return false;
    }

    private Path path(String kind) { return directory.resolve(String.format(Locale.ROOT,"%06d.%s.json",sequence,kind)); }

    private void publish(String status,String error) {
        JsonObject response = new JsonObject();
        response.addProperty("schema","sfm.managed-session-response/1");
        response.addProperty("sequence",sequence);
        response.addProperty("op",operation);
        response.addProperty("status",status);
        response.addProperty("duration_ms",(System.nanoTime()-requestStarted)/1_000_000L);
        if (error != null) response.addProperty("error",error);
        write(path("response"),response);
        SFM.LOGGER.info("SFM_MANAGED_SESSION_RESPONSE sequence={} op={} status={}",sequence,operation,status);
        sequence++;
        pending=List.of();
    }
    private static JsonObject read(Path path) {
        if (Files.notExists(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return null;
        try (var input = Files.newInputStream(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = input.readNBytes(16_385);
            if (bytes.length > 16_384) throw new java.io.IOException("Managed JSON exceeds byte limit");
            return com.google.gson.JsonParser.parseString(new String(bytes, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException | RuntimeException exception) {
            throw new IllegalStateException("Cannot read bounded managed request", exception);
        }
    }

    private static void write(Path path, JsonObject value) {
        byte[] bytes = value.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (bytes.length > 16_384) throw new IllegalArgumentException("Managed JSON exceeds byte limit");
        try {
            Path temporary = path.resolveSibling(path.getFileName() + ".writing");
            Files.write(temporary, bytes, java.nio.file.StandardOpenOption.CREATE_NEW,
                    java.nio.file.StandardOpenOption.WRITE, java.nio.file.LinkOption.NOFOLLOW_LINKS);
            Files.move(temporary, path, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Cannot publish managed response", exception);
        }
    }

}
{% endif %}
{% endcase %}
