package ca.teamdman.sfm.client.review.release_review;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class SFMReviewEvidenceExportFileTests {
    @TempDir Path directory;
    private String authority() {
        return SFMReleaseReviewLedgerV3Codec.write(SFMReleaseReviewLedgerV3.create("export", "Export", List.of(
                new SFMReleaseReviewLedgerV3.TargetLane("1.19.2", "sfm", ".", "1".repeat(40),
                        Optional.empty(), List.of("."), List.of(), true))));
    }
    @Test void publishesNewFileWithoutChangingSourceAndRefusesExistingDestination() throws Exception {
        Path source = directory.resolve("source.json"), output = directory.resolve("export.json");
        String authority = authority();
        Files.writeString(source, authority);
        var prepared = SFMReviewEvidenceExport.prepare(authority, SFMReviewEvidenceExport.Policy.EMBED_COMMENT_EVIDENCE, Map.of());
        var result = SFMReviewEvidenceExportFile.publish(source, output, prepared);
        assertEquals(authority, Files.readString(source));
        assertEquals(prepared.output(), Files.readString(output));
        assertEquals(Files.size(output), result.bytes());
        assertThrows(java.nio.file.FileAlreadyExistsException.class, () -> SFMReviewEvidenceExportFile.publish(source, output, prepared));
        assertEquals(prepared.output(), Files.readString(output));
        try (var files = Files.list(directory)) { assertEquals(2, files.count()); }
    }
    @Test void rejectsChangedSourceAndSourceAsDestinationWithoutPublishing() throws Exception {
        Path source = directory.resolve("source.json"), output = directory.resolve("export.json");
        String authority = authority();
        Files.writeString(source, authority);
        var prepared = SFMReviewEvidenceExport.prepare(authority, SFMReviewEvidenceExport.Policy.EMBED_COMMENT_EVIDENCE, Map.of());
        assertThrows(java.io.IOException.class, () -> SFMReviewEvidenceExportFile.publish(source, source, prepared));
        Files.writeString(source, authority + "\n");
        assertThrows(java.io.IOException.class, () -> SFMReviewEvidenceExportFile.publish(source, output, prepared));
        assertFalse(Files.exists(output));
        assertEquals(authority + "\n", Files.readString(source));
    }
}
