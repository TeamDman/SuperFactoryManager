package ca.teamdman.sfm.client.command;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMCommandHistoryTests {
    @TempDir
    Path tempDir;

    @AfterEach
    void resetService() {
        SFMCommandHistoryService.resetForTests();
    }

    @Test
    void preservesRepeatsForTheDocumentButDeduplicatesMruSuggestions() {
        var history = SFMCommandHistory.inMemory();
        history.record(" /sfm action invoke sfm:echo one ");
        history.record("sfm action invoke sfm:echo two");
        history.record("sfm action invoke sfm:echo one");

        assertEquals(List.of(
                "sfm action invoke sfm:echo one",
                "sfm action invoke sfm:echo two",
                "sfm action invoke sfm:echo one"), history.entriesOldestFirst());
        assertEquals(List.of(
                "sfm action invoke sfm:echo one",
                "sfm action invoke sfm:echo two"), history.suggestionsNewestFirst());
        assertEquals(String.join("\n", history.entriesOldestFirst()), history.documentText());
    }

    @Test
    void enforcesEntryAndCommandBounds() {
        var history = SFMCommandHistory.inMemory();
        for (int index = 0; index < SFMCommandHistory.MAX_ENTRIES + 25; index++) {
            history.record("sfm action invoke sfm:echo " + index);
        }
        assertEquals(SFMCommandHistory.MAX_ENTRIES, history.entriesOldestFirst().size());
        assertEquals("sfm action invoke sfm:echo 25", history.entriesOldestFirst().get(0));

        history.record("sfm action invoke sfm:echo " + "x".repeat(SFMCommandHistory.MAX_COMMAND_LENGTH));
        assertEquals(SFMCommandHistory.MAX_ENTRIES, history.entriesOldestFirst().size());
        history.record("sfm action invoke sfm:echo " + "x".repeat(SFMCommandHistory.MAX_COMMAND_LENGTH + 1));
        assertEquals(SFMCommandHistory.MAX_ENTRIES, history.entriesOldestFirst().size());
    }

    @Test
    void persistenceIsCalledAfterTheSnapshotLeavesTheHistoryLock() {
        AtomicReference<List<String>> persisted = new AtomicReference<>();
        var history = new SFMCommandHistory(List.of(), snapshot -> {
            persisted.set(snapshot);
            assertEquals(List.of("sfm action invoke sfm:echo ok"), snapshot);
        }, Runnable::run);

        history.record("sfm action invoke sfm:echo ok");
        assertEquals(List.of("sfm action invoke sfm:echo ok"), persisted.get());
    }

    @Test
    void codecRoundTripsAndRecoversCorruptionWithoutThrowing() {
        List<String> entries = List.of(
                "sfm action invoke sfm:echo café",
                "sfm action invoke sfm:echo second");
        var parsed = SFMCommandHistoryCodec.parse(SFMCommandHistoryCodec.serialize(entries));
        assertFalse(parsed.corrupt());
        assertEquals(entries, parsed.entries());

        var corrupt = SFMCommandHistoryCodec.parse("sfm-command-history schema=1\nnot-base64!!!\n");
        assertTrue(corrupt.corrupt());
        assertEquals(List.of(), corrupt.entries());
    }

    @Test
    void clearPersistsAnEmptySnapshot() {
        List<List<String>> writes = new ArrayList<>();
        var history = new SFMCommandHistory(List.of("sfm action invoke sfm:echo old"), writes::add, Runnable::run);
        history.clear();
        assertEquals(List.of(), history.entriesOldestFirst());
        assertEquals(List.of(List.of()), writes);
    }

    @Test
    void disabledStartupDoesNotReadOrRecordAndReenableReloadsRetainedFile() throws IOException {
        Path path = tempDir.resolve("sfm-command-history.v1");
        String persisted = SFMCommandHistoryCodec.serialize(List.of("sfm action invoke sfm:echo retained"));
        Files.writeString(path, persisted, StandardCharsets.UTF_8);

        SFMCommandHistoryService.initialize(path, false);

        assertFalse(SFMCommandHistoryService.isPersistenceEnabled());
        assertEquals(List.of(), SFMCommandHistoryService.suggestionsNewestFirst());
        SFMCommandHistoryService.recordSuccessful("sfm action invoke sfm:echo ignored");
        assertEquals(persisted, Files.readString(path, StandardCharsets.UTF_8));

        SFMCommandHistoryService.setPersistenceEnabled(true);

        assertTrue(SFMCommandHistoryService.isPersistenceEnabled());
        assertEquals(List.of("sfm action invoke sfm:echo retained"),
                SFMCommandHistoryService.suggestionsNewestFirst());
    }

    @Test
    void clearWhileDisabledExplicitlyErasesRetainedFile() throws IOException {
        Path path = tempDir.resolve("sfm-command-history.v1");
        Files.writeString(path, SFMCommandHistoryCodec.serialize(
                List.of("sfm action invoke sfm:echo retained")), StandardCharsets.UTF_8);

        SFMCommandHistoryService.initialize(path, false);
        SFMCommandHistoryService.clear();

        assertFalse(Files.exists(path));
        SFMCommandHistoryService.setPersistenceEnabled(true);
        assertEquals(List.of(), SFMCommandHistoryService.suggestionsNewestFirst());
    }
}
