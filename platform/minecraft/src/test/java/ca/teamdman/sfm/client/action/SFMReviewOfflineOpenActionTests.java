package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.review.release_review.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class SFMReviewOfflineOpenActionTests {
    @TempDir Path directory;
    @Test void fileOutsideAnyRepositoryCanBeReadWithoutChangingIt() throws Exception {
        var ledger = SFMReleaseReviewLedgerV3.create("offline", "Offline test", List.of(
                new SFMReleaseReviewLedgerV3.TargetLane("test", "unavailable", "missing/repository",
                        "1".repeat(40), Optional.empty(), List.of("."), List.of(), true)));
        Path file = directory.resolve("review with spaces.sfm-review.json");
        String original = SFMReleaseReviewLedgerV3Codec.write(ledger);
        Files.writeString(file, original);
        var tree = SFMReviewOfflineOpenAction.read(file);
        assertTrue(tree.nodes().stream().anyMatch(node -> node.entry().label().contains("current source status unavailable")));
        assertEquals(original, Files.readString(file));
        try (var files = Files.list(directory)) { assertEquals(1, files.count()); }
    }
    @Test void invalidUtf8InvalidSchemaAndAbsentFileFailWithoutCreatingAnything() throws Exception {
        Path file = directory.resolve("invalid.sfm-review.json");
        Files.write(file, new byte[]{(byte) 0xC3, 0x28});
        assertThrows(java.io.IOException.class, () -> SFMReviewOfflineOpenAction.read(file));
        Files.writeString(file, "{\"schema\":\"sfm.release-review/1\"}");
        assertThrows(RuntimeException.class, () -> SFMReviewOfflineOpenAction.read(file));
        assertThrows(java.io.IOException.class, () -> SFMReviewOfflineOpenAction.read(directory.resolve("absent.json")));
        try (var files = Files.list(directory)) { assertEquals(1, files.count()); }
    }
}
