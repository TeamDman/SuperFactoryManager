package ca.teamdman.sfm.client.command;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.config.SFMConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Process-wide history owner; completion only observes its in-memory snapshot. */
public final class SFMCommandHistoryService {
    private static final String FILE_NAME = "sfm-command-history.v1";
    private static volatile SFMCommandHistory active = SFMCommandHistory.inMemory();
    private static volatile Path activePath;
    private static volatile ExecutorService persistenceExecutor;
    private static volatile boolean persistenceEnabled = true;
    private static volatile long activeGeneration;

    private SFMCommandHistoryService() {
    }

    /** Loads once during client setup, before the palette can ask for suggestions. */
    public static synchronized void initializeDefault() {
        initialize(
                Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve(FILE_NAME),
                SFMConfig.getOrDefault(SFMConfig.CLIENT_CONFIG.commandPaletteHistoryEnabled)
        );
    }

    public static synchronized void initialize(Path path) {
        initialize(path, true);
    }

    public static synchronized void initialize(Path path, boolean enabled) {
        shutdownPersistenceExecutor();
        long generation = ++activeGeneration;
        List<String> entries = enabled ? readHistory(path) : List.of();
        ExecutorService executor = enabled ? createPersistenceExecutor() : null;
        Executor persistence = executor == null ? Runnable::run : executor;
        SFMCommandHistory[] holder = new SFMCommandHistory[1];
        holder[0] = new SFMCommandHistory(entries, snapshot -> {
            if (persistenceEnabled && activeGeneration == generation && active == holder[0]) {
                writeAtomically(path, snapshot);
            }
        }, persistence);
        activePath = path;
        persistenceExecutor = executor;
        persistenceEnabled = enabled;
        active = holder[0];
    }

    public static List<String> snapshotOldestFirst() {
        return active.entriesOldestFirst();
    }

    public static List<String> suggestionsNewestFirst() {
        return active.suggestionsNewestFirst();
    }

    public static String documentText() {
        return active.documentText();
    }

    public static void recordSuccessful(String command) {
        if (persistenceEnabled) active.record(command);
    }

    public static boolean isRecordable(String command) {
        if (!persistenceEnabled || command == null || !command.startsWith("sfm action invoke ")) return false;
        String suffix = command.substring("sfm action invoke ".length()).stripLeading();
        int separator = 0;
        while (separator < suffix.length() && !Character.isWhitespace(suffix.charAt(separator))) separator++;
        ResourceLocation actionId = separator == 0
                ? null
                : ResourceLocation.tryParse(suffix.substring(0, separator));
        return actionId == null || !actionId.getNamespace().equals("sfm")
                || !actionId.getPath().startsWith("palette/history/");
    }

    public static void clear() {
        active.clear();
        if (!persistenceEnabled) deletePersistedHistory();
    }

    public static boolean isPersistenceEnabled() {
        return persistenceEnabled;
    }

    /** Switches the active snapshot immediately; the persisted file is retained while disabled. */
    public static synchronized void setPersistenceEnabled(boolean enabled) {
        if (persistenceEnabled == enabled) return;
        if (activePath == null) {
            shutdownPersistenceExecutor();
            ++activeGeneration;
            persistenceEnabled = enabled;
            active = SFMCommandHistory.inMemory();
            return;
        }
        initialize(activePath, enabled);
    }

    public static Path activePath() {
        return activePath;
    }

    public static boolean hasPersistentStorage() {
        return activePath != null;
    }

    /** In-memory injection seam for puppet and unit tests. */
    public static synchronized void installForTests(SFMCommandHistory history) {
        shutdownPersistenceExecutor();
        ++activeGeneration;
        active = history;
        activePath = null;
        persistenceEnabled = true;
    }

    public static synchronized void resetForTests() {
        shutdownPersistenceExecutor();
        ++activeGeneration;
        active = SFMCommandHistory.inMemory();
        activePath = null;
        persistenceEnabled = true;
    }

    private static List<String> readHistory(Path path) {
        try {
            if (Files.isRegularFile(path)) {
                SFMCommandHistoryCodec.ParseResult result = SFMCommandHistoryCodec.parse(
                        Files.readString(path, StandardCharsets.UTF_8));
                if (result.corrupt()) {
                    SFM.LOGGER.warn("Ignoring corrupt SFM command history at {}: {}", path, result.diagnostic());
                }
                return result.entries();
            }
        } catch (IOException exception) {
            SFM.LOGGER.warn("Unable to read SFM command history at {}", path, exception);
        }
        return List.of();
    }

    private static ExecutorService createPersistenceExecutor() {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "sfm-command-history-persistence");
            thread.setDaemon(true);
            return thread;
        });
    }

    private static void shutdownPersistenceExecutor() {
        ExecutorService executor = persistenceExecutor;
        persistenceExecutor = null;
        if (executor != null) executor.shutdownNow();
    }

    private static void deletePersistedHistory() {
        Path path = activePath;
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            SFM.LOGGER.warn("Unable to clear disabled SFM command history at {}", path, exception);
        }
    }

    private static void writeAtomically(Path path, List<String> entries) {
        try {
            Files.createDirectories(path.getParent());
            Path temporary = Files.createTempFile(path.getParent(), path.getFileName().toString(), ".tmp");
            try {
                Files.writeString(temporary, SFMCommandHistoryCodec.serialize(entries), StandardCharsets.UTF_8);
                try {
                    Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException ignored) {
                    Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException exception) {
            SFM.LOGGER.warn("Unable to persist SFM command history at {}", path, exception);
        }
    }
}
