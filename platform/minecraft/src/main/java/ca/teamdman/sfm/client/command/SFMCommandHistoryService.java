package ca.teamdman.sfm.client.command;

import ca.teamdman.sfm.SFM;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.resources.ResourceLocation;

/** Process-wide history owner; completion only observes its in-memory snapshot. */
public final class SFMCommandHistoryService {
    private static final String FILE_NAME = "sfm-command-history.v1";
    private static volatile SFMCommandHistory active = SFMCommandHistory.inMemory();
    private static volatile Path activePath;

    private SFMCommandHistoryService() {
    }

    /** Loads once during client setup, before the palette can ask for suggestions. */
    public static synchronized void initializeDefault() {
        initialize(Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve(FILE_NAME));
    }

    public static synchronized void initialize(Path path) {
        List<String> entries = List.of();
        try {
            if (Files.isRegularFile(path)) {
                SFMCommandHistoryCodec.ParseResult result = SFMCommandHistoryCodec.parse(
                        Files.readString(path, StandardCharsets.UTF_8));
                entries = result.entries();
                if (result.corrupt()) {
                    SFM.LOGGER.warn("Ignoring corrupt SFM command history at {}: {}", path, result.diagnostic());
                }
            }
        } catch (IOException exception) {
            SFM.LOGGER.warn("Unable to read SFM command history at {}", path, exception);
        }

        ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "sfm-command-history-persistence");
            thread.setDaemon(true);
            return thread;
        });
        activePath = path;
        active = new SFMCommandHistory(entries,
                snapshot -> writeAtomically(path, snapshot), executor);
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
        active.record(command);
    }

    public static boolean isRecordable(String command) {
        if (command == null || !command.startsWith("sfm action invoke ")) return false;
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
    }

    public static Path activePath() {
        return activePath;
    }

    /** In-memory injection seam for puppet and unit tests. */
    public static synchronized void installForTests(SFMCommandHistory history) {
        active = history;
        activePath = null;
    }

    public static synchronized void resetForTests() {
        active = SFMCommandHistory.inMemory();
        activePath = null;
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
